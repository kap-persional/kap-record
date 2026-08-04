package com.kap.record.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.kap.record.Constants
import com.kap.record.describeForUser
import com.kap.record.capture.AudioCapture
import com.kap.record.capture.AudioQuality
import com.kap.record.capture.MuxerController
import com.kap.record.capture.ScreenEncoder
import com.kap.record.capture.VideoQuality
import com.kap.record.capture.computeCaptureDimensions
import com.kap.record.notification.RecordingNotification
import com.kap.record.output.OutputFileManager
import com.kap.record.output.RecordingOutputTarget
import com.kap.record.state.RecordingState
import com.kap.record.state.RecordingStateHolder

data class StopResult(
    val uri: String,
    val displayPath: String,
    val durationMs: Long,
    val sizeBytes: Long
)

/**
 * Foreground service điều phối toàn bộ vòng đời ghi hình:
 * IDLE -> ARMED (đã có MediaProjection, chưa mã hoá) -> RECORDING <-> PAUSED -> STOPPING -> IDLE.
 *
 * Có 2 cách gọi vào: qua bindService (RecorderChannelHandler dùng cho start/stop để nhận
 * kết quả trả về) và qua Intent action khởi bằng startService (Notification, Quick Settings
 * Tile — nơi không có sẵn kết nối bind).
 */
class ScreenRecordService : Service() {

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var screenEncoder: ScreenEncoder? = null
    private var audioCapture: AudioCapture? = null
    private var muxerController: MuxerController? = null
    private var outputTarget: RecordingOutputTarget? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null
    private var videoQuality = VideoQuality.MEDIUM
    private var audioQuality = AudioQuality.MEDIUM
    private var stopCallback: ((StopResult?, String?) -> Unit)? = null
    private var recordingStartWallClockMs = 0L

    private val tickerRunnable = object : Runnable {
        override fun run() {
            RecordingStateHolder.tick(this@ScreenRecordService)
            updateNotificationTick()
            mainHandler.postDelayed(this, 1000)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onCreate() {
        super.onCreate()
        RecordingStateHolder.ensureRestored(this)
        RecordingNotification.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_ARM -> handleArmIntent(intent)
            Constants.ACTION_PAUSE -> performPause()
            Constants.ACTION_RESUME -> performResume()
            Constants.ACTION_STOP -> performStop(null)
            Constants.ACTION_CANCEL -> performCancelArmed()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------
    // ARM: nhận sự đồng ý MediaProjection, khởi foreground service ngay.
    // ------------------------------------------------------------------

    private fun handleArmIntent(intent: Intent) {
        val resultCode = intent.getIntExtra(Constants.EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent.getParcelableExtra(Constants.EXTRA_RESULT_DATA)
        if (resultData == null) {
            Log.e(TAG, "Thiếu dữ liệu đồng ý MediaProjection")
            stopSelf()
            return
        }

        val notification = RecordingNotification.buildArmed(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "Không lấy được MediaProjection")
            RecordingStateHolder.reset(this, "Không thể khởi tạo quyền ghi màn hình")
            stopSelf()
            return
        }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // Hệ thống thu hồi quyền ghi màn hình (ví dụ người dùng bấm nút Dừng của
                // chip hệ thống Android 12+) — kết thúc gọn thay vì để crash.
                performStop(null)
            }
        }
        projection.registerCallback(callback, mainHandler)

        mediaProjection = projection
        projectionCallback = callback
        RecordingStateHolder.markArmed(this)
    }

    private fun performCancelArmed() {
        if (RecordingStateHolder.state != RecordingState.ARMED) return
        releaseProjection()
        RecordingStateHolder.reset(this)
        stopForegroundCompat()
        stopSelf()
    }

    // ------------------------------------------------------------------
    // START (chỉ gọi qua bindService — cần trả kết quả/lỗi về cho Dart)
    // ------------------------------------------------------------------

    fun startRecordingRequest(
        videoQualityWire: String?,
        audioQualityWire: String?,
        saveMode: String?,
        customUri: String?,
        callback: (Boolean, String?) -> Unit
    ) {
        if (RecordingStateHolder.state != RecordingState.ARMED) {
            callback(false, "Chưa sẵn sàng ghi hình (thiếu quyền MediaProjection)")
            return
        }
        val projection = mediaProjection
        if (projection == null) {
            callback(false, "Phiên ghi màn hình đã hết hạn, hãy thử lại")
            return
        }

        videoQuality = VideoQuality.fromWireName(videoQualityWire)
        audioQuality = AudioQuality.fromWireName(audioQualityWire)

        try {
            val target = OutputFileManager.createTarget(this, saveMode, customUri)
            outputTarget = target
            val pfd = target.openFileDescriptor(this)
            outputPfd = pfd
            val muxer = MuxerController(pfd)
            muxerController = muxer

            val metrics = realDisplayMetrics()
            val dimensions = computeCaptureDimensions(metrics.widthPixels, metrics.heightPixels, videoQuality)

            val encoder = ScreenEncoder(
                mediaProjection = projection,
                dimensions = dimensions,
                quality = videoQuality,
                densityDpi = metrics.densityDpi,
                onFormatReady = { format -> muxer.addVideoTrack(format) },
                onEncodedFrame = { track, buffer, info -> muxer.writeSample(track, buffer, info) },
                onCodecError = { t -> handleCaptureError(t) }
            )
            val audio = AudioCapture(
                context = this,
                mediaProjection = projection,
                quality = audioQuality,
                onFormatReady = { format -> muxer.addAudioTrack(format) },
                onEncodedFrame = { track, buffer, info -> muxer.writeSample(track, buffer, info) },
                onCodecError = { t -> handleCaptureError(t) }
            )
            screenEncoder = encoder
            audioCapture = audio

            encoder.start()
            audio.start()

            recordingStartWallClockMs = System.currentTimeMillis()
            RecordingStateHolder.markRecordingStarted(this)
            mainHandler.post(tickerRunnable)
            mainHandler.post { updateNotificationTick() }
            scheduleStartWatchdog(muxer)
            callback(true, null)
        } catch (t: Throwable) {
            Log.e(TAG, "Không thể bắt đầu ghi hình", t)
            cleanupAfterFailure()
            callback(false, t.describeForUser())
        }
    }

    /**
     * Nếu sau [START_WATCHDOG_MS] mà MediaMuxer vẫn chưa thực sự start() được (thường do
     * track audio không bao giờ sẵn sàng — ví dụ AudioRecord không khởi tạo được trên máy
     * cụ thể), toàn bộ khung hình đã và đang bị lặng lẽ bỏ qua dù UI vẫn hiển thị "đang ghi"
     * bình thường. Phải phát hiện và huỷ sớm, tránh để buổi ghi chạy hết cả buổi rồi mới lộ
     * ra file trống/hỏng khi mở lên xem.
     */
    private fun scheduleStartWatchdog(muxer: MuxerController) {
        mainHandler.postDelayed({
            if (RecordingStateHolder.state == RecordingState.RECORDING && !muxer.hasStarted()) {
                Log.e(TAG, "Watchdog: muxer chưa start sau ${START_WATCHDOG_MS}ms — huỷ buổi ghi")
                cleanupAfterFailure(
                    "Không thể khởi tạo ghi âm thanh nội bộ trên thiết bị này — đã huỷ buổi ghi để " +
                        "tránh tạo file hỏng. Hãy thử lại hoặc kiểm tra quyền Micro trong Cài đặt."
                )
            }
        }, START_WATCHDOG_MS)
    }

    private fun handleCaptureError(t: Throwable) {
        Log.e(TAG, "Lỗi trong quá trình mã hoá", t)
        mainHandler.post {
            if (RecordingStateHolder.state == RecordingState.RECORDING ||
                RecordingStateHolder.state == RecordingState.PAUSED
            ) {
                performStop(null, error = t.describeForUser())
            }
        }
    }

    // ------------------------------------------------------------------
    // PAUSE / RESUME
    // ------------------------------------------------------------------

    private fun performPause() {
        if (RecordingStateHolder.state != RecordingState.RECORDING) return
        screenEncoder?.pause()
        audioCapture?.pause()
        RecordingStateHolder.markPaused(this)
        updateNotificationTick()
    }

    private fun performResume() {
        if (RecordingStateHolder.state != RecordingState.PAUSED) return
        screenEncoder?.resume()
        audioCapture?.resume()
        RecordingStateHolder.markResumed(this)
        updateNotificationTick()
    }

    // ------------------------------------------------------------------
    // STOP (Intent hoặc bindService)
    // ------------------------------------------------------------------

    fun stopRecordingRequest(callback: (StopResult?, String?) -> Unit) {
        performStop(callback)
    }

    private fun performStop(callback: ((StopResult?, String?) -> Unit)?, error: String? = null) {
        val state = RecordingStateHolder.state
        if (state == RecordingState.ARMED) {
            performCancelArmed()
            callback?.invoke(null, error ?: "Đã huỷ")
            return
        }
        if (state != RecordingState.RECORDING && state != RecordingState.PAUSED) {
            callback?.invoke(null, error ?: "Không có bản ghi nào đang chạy")
            return
        }

        stopCallback = callback
        RecordingStateHolder.markStopping(this)
        mainHandler.removeCallbacks(tickerRunnable)

        val encoder = screenEncoder
        val audio = audioCapture
        // signalEndOfInputStream() hoạt động độc lập với việc Surface có đang được VirtualDisplay
        // gắn hay không, và AudioCapture.stop() thoát vòng lặp nền ngay cả khi đang paused —
        // nên không cần resume() trước khi dừng.
        encoder?.signalEndOfStream()
        audio?.stop()

        Thread {
            audio?.awaitFinished()
            waitForVideoDrain()
            mainHandler.post { finalizeRecording(error) }
        }.start()
    }

    private fun waitForVideoDrain() {
        val muxer = muxerController ?: return
        var waited = 0
        while (!muxer.videoDone && waited < 5000) {
            Thread.sleep(50)
            waited += 50
        }
        // Khoảng đệm nhỏ để luồng callback của MediaCodec kịp gọi releaseOutputBuffer()
        // cho buffer EOS cuối cùng trước khi ta stop()/release() codec ở luồng khác.
        Thread.sleep(80)
    }

    private fun finalizeRecording(error: String?) {
        val muxer = muxerController
        val target = outputTarget
        val encoder = screenEncoder
        val audio = audioCapture

        runCatching { encoder?.release() }
        runCatching { audio?.release() }

        var result: StopResult? = null
        var resultError = error

        try {
            if (muxer != null && target != null) {
                if (!muxer.hasStarted()) {
                    // Muxer chưa bao giờ thực sự start() (thiếu track audio hoặc video) — không có
                    // nội dung thật nào được ghi. Xoá file dở dang thay vì để lại một video
                    // trống/hỏng hiển thị trong Gallery như file ghi thành công.
                    muxer.finalizeAndRelease()
                    runCatching { outputPfd?.close() }
                    outputPfd = null
                    target.deleteIfIncomplete(this)
                    resultError = resultError
                        ?: "Ghi hình thất bại — không ghi được dữ liệu (âm thanh nội bộ có thể chưa khởi tạo được). Hãy thử lại."
                } else {
                    val durationMs = muxer.durationMs()
                    muxer.finalizeAndRelease()
                    val sizeBytes = runCatching { outputPfd?.statSize ?: 0L }.getOrDefault(0L)
                    runCatching { outputPfd?.close() }
                    outputPfd = null
                    val finalSize = target.finalizeOutput(this, sizeBytes)
                    result = StopResult(
                        uri = target.uri.toString(),
                        displayPath = target.displayName,
                        durationMs = durationMs,
                        sizeBytes = finalSize
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi khi hoàn tất file ghi hình", t)
            resultError = t.describeForUser()
            runCatching { outputPfd?.close() }
            outputPfd = null
            runCatching { target?.deleteIfIncomplete(this) }
        }

        releaseProjection()
        screenEncoder = null
        audioCapture = null
        muxerController = null
        outputTarget = null

        RecordingStateHolder.reset(this, if (result == null) resultError else null)
        stopForegroundCompat()
        stopSelf()

        val cb = stopCallback
        stopCallback = null
        cb?.invoke(result, if (result == null) resultError else null)
    }

    private fun cleanupAfterFailure(reason: String? = null) {
        mainHandler.removeCallbacks(tickerRunnable)
        val encoder = screenEncoder
        val audio = audioCapture
        // Dừng nguồn trước rồi mới release, tránh đụng độ với luồng nền của AudioCapture
        // nếu nó vẫn đang chạy (ví dụ watchdog huỷ một buổi ghi đang diễn ra).
        runCatching { encoder?.signalEndOfStream() }
        runCatching { audio?.stop() }
        Thread {
            runCatching { audio?.awaitFinished(1500) }
            Thread.sleep(100)
            mainHandler.post {
                runCatching { encoder?.release() }
                runCatching { audio?.release() }
                runCatching { muxerController?.finalizeAndRelease() }
                runCatching { outputPfd?.close() }
                outputPfd = null
                runCatching { outputTarget?.deleteIfIncomplete(this) }
                screenEncoder = null
                audioCapture = null
                muxerController = null
                outputTarget = null
                releaseProjection()
                RecordingStateHolder.reset(this, reason)
                stopForegroundCompat()
                stopSelf()
            }
        }.start()
    }

    private fun releaseProjection() {
        mediaProjection?.let { projection ->
            projectionCallback?.let { runCatching { projection.unregisterCallback(it) } }
            runCatching { projection.stop() }
        }
        mediaProjection = null
        projectionCallback = null
    }

    private fun updateNotificationTick() {
        val state = RecordingStateHolder.state
        if (state != RecordingState.RECORDING && state != RecordingState.PAUSED) return
        val notification = RecordingNotification.buildRecording(
            this,
            RecordingStateHolder.currentElapsedMs(),
            RecordingStateHolder.isPaused
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun stopForegroundCompat() {
        mainHandler.removeCallbacks(tickerRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun realDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickerRunnable)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val START_WATCHDOG_MS = 5000L
    }
}
