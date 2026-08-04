package com.kap.record.channel

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import com.kap.record.Constants
import com.kap.record.service.ScreenRecordService
import com.kap.record.state.RecordingState
import com.kap.record.state.RecordingStateHolder
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Cầu nối MethodChannel/EventChannel <-> ScreenRecordService. Dùng chung cho MainActivity
 * (luồng mở từ trong app) và CountdownActivity (luồng mở từ Quick Settings Tile) — mỗi
 * Activity có FlutterEngine riêng nhưng đều bind vào CÚNG MỘT ScreenRecordService
 * (singleton trong tiến trình), nên trạng thái luôn nhất quán.
 */
class RecorderChannelHandler(private val activity: Activity) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler, RecordingStateHolder.Listener {

    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null
    private var pendingConsentResult: MethodChannel.Result? = null
    private var pendingFolderResult: MethodChannel.Result? = null
    private var boundService: ScreenRecordService? = null
    private var serviceConnection: ServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(flutterEngine: FlutterEngine) {
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        methodChannel = MethodChannel(messenger, Constants.METHOD_CHANNEL).also {
            it.setMethodCallHandler(this)
        }
        eventChannel = EventChannel(messenger, Constants.EVENT_CHANNEL).also {
            it.setStreamHandler(this)
        }
        bindService()
    }

    fun detach() {
        methodChannel?.setMethodCallHandler(null)
        eventChannel?.setStreamHandler(null)
        methodChannel = null
        eventChannel = null
        RecordingStateHolder.removeListener(this)
        unbindService()
    }

    private fun bindService() {
        val intent = Intent(activity, ScreenRecordService::class.java)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                boundService = (service as? ScreenRecordService.LocalBinder)?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
        serviceConnection = connection
        activity.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        serviceConnection?.let { runCatching { activity.applicationContext.unbindService(it) } }
        serviceConnection = null
        boundService = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            Constants.METHOD_REQUEST_PROJECTION_CONSENT -> requestConsent(result)
            Constants.METHOD_START_RECORDING -> startRecording(call, result)
            Constants.METHOD_PAUSE_RECORDING -> {
                sendServiceAction(Constants.ACTION_PAUSE)
                result.success(null)
            }
            Constants.METHOD_RESUME_RECORDING -> {
                sendServiceAction(Constants.ACTION_RESUME)
                result.success(null)
            }
            Constants.METHOD_STOP_RECORDING -> stopRecording(result)
            Constants.METHOD_CANCEL_ARMED -> {
                sendServiceAction(Constants.ACTION_CANCEL)
                result.success(null)
            }
            Constants.METHOD_PICK_SAVE_FOLDER -> pickFolder(result)
            Constants.METHOD_GET_CURRENT_STATE -> result.success(currentStateMap())
            else -> result.notImplemented()
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(activity, ScreenRecordService::class.java).setAction(action)
        activity.startService(intent)
    }

    private fun requestConsent(result: MethodChannel.Result) {
        pendingConsentResult = result
        val manager =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE_PROJECTION)
    }

    private fun startRecording(call: MethodCall, result: MethodChannel.Result) {
        val videoQuality = call.argument<String>("videoQuality")
        val audioQuality = call.argument<String>("audioQuality")
        val saveMode = call.argument<String>("saveMode")
        val customUri = call.argument<String>("customUri")
        withBoundService(result) { service ->
            service.startRecordingRequest(videoQuality, audioQuality, saveMode, customUri) { success, error ->
                mainHandler.post {
                    if (success) result.success(null) else result.error("START_FAILED", error, null)
                }
            }
        }
    }

    private fun stopRecording(result: MethodChannel.Result) {
        withBoundService(result) { service ->
            service.stopRecordingRequest { stopResult, error ->
                mainHandler.post {
                    if (stopResult != null) {
                        result.success(
                            mapOf(
                                "uri" to stopResult.uri,
                                "displayPath" to stopResult.displayPath,
                                "durationMs" to stopResult.durationMs,
                                "sizeBytes" to stopResult.sizeBytes
                            )
                        )
                    } else {
                        result.error("STOP_FAILED", error ?: "Không thể hoàn tất ghi hình", null)
                    }
                }
            }
        }
    }

    /**
     * bindService() là bất đồng bộ — nếu Activity vừa mở (ví dụ người dùng bắt đầu ghi từ
     * Quick Settings Tile rồi mở app lên để bấm Dừng) và người dùng thao tác ngay lập tức,
     * boundService có thể vẫn còn null dù Service đã đang chạy sẵn. Thử lại trong một
     * khoảng ngắn (~2 giây, bind cùng tiến trình thường chỉ mất vài ms) trước khi thực sự
     * báo lỗi, thay vì báo lỗi ngay khiến người dùng tưởng nút bấm không có tác dụng.
     */
    private fun withBoundService(
        result: MethodChannel.Result,
        retriesLeft: Int = MAX_BIND_RETRIES,
        action: (ScreenRecordService) -> Unit
    ) {
        val service = boundService
        if (service != null) {
            action(service)
            return
        }
        if (retriesLeft <= 0) {
            result.error("SERVICE_NOT_READY", "Dịch vụ ghi hình chưa sẵn sàng, hãy thử lại", null)
            return
        }
        mainHandler.postDelayed({ withBoundService(result, retriesLeft - 1, action) }, BIND_RETRY_DELAY_MS)
    }

    private fun pickFolder(result: MethodChannel.Result) {
        pendingFolderResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        activity.startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
    }

    /** Gọi từ Activity.onActivityResult() của MainActivity/CountdownActivity. */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val armIntent = Intent(activity, ScreenRecordService::class.java).apply {
                        action = Constants.ACTION_ARM
                        putExtra(Constants.EXTRA_RESULT_CODE, resultCode)
                        putExtra(Constants.EXTRA_RESULT_DATA, data)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(activity, armIntent)
                    pendingConsentResult?.success(true)
                } else {
                    pendingConsentResult?.success(false)
                }
                pendingConsentResult = null
            }

            REQUEST_CODE_PICK_FOLDER -> {
                val uri = data?.data
                if (resultCode == Activity.RESULT_OK && uri != null) {
                    runCatching {
                        activity.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    val displayName = DocumentFile.fromTreeUri(activity, uri)?.name ?: uri.toString()
                    pendingFolderResult?.success(mapOf("uri" to uri.toString(), "displayName" to displayName))
                } else {
                    pendingFolderResult?.success(null)
                }
                pendingFolderResult = null
            }
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        RecordingStateHolder.ensureRestored(activity)
        RecordingStateHolder.addListener(this)
        onStateChanged(
            RecordingStateHolder.state,
            RecordingStateHolder.isPaused,
            RecordingStateHolder.currentElapsedMs(),
            null,
            RecordingStateHolder.audioLikelySilent
        )
    }

    override fun onCancel(arguments: Any?) {
        RecordingStateHolder.removeListener(this)
        eventSink = null
    }

    override fun onStateChanged(
        state: RecordingState,
        isPaused: Boolean,
        elapsedMs: Long,
        error: String?,
        audioLikelySilent: Boolean
    ) {
        mainHandler.post {
            eventSink?.success(stateMap(state, isPaused, elapsedMs, error, audioLikelySilent))
        }
    }

    private fun currentStateMap(): Map<String, Any?> {
        RecordingStateHolder.ensureRestored(activity)
        return stateMap(
            RecordingStateHolder.state,
            RecordingStateHolder.isPaused,
            RecordingStateHolder.currentElapsedMs(),
            null,
            RecordingStateHolder.audioLikelySilent
        )
    }

    private fun stateMap(
        state: RecordingState,
        isPaused: Boolean,
        elapsedMs: Long,
        error: String?,
        audioLikelySilent: Boolean
    ) = mapOf(
        "state" to state.name.lowercase(),
        "elapsedSeconds" to (elapsedMs / 1000).toInt(),
        "isPaused" to isPaused,
        "error" to error,
        "audioLikelySilent" to audioLikelySilent
    )

    companion object {
        private const val REQUEST_CODE_PROJECTION = 9821
        private const val REQUEST_CODE_PICK_FOLDER = 9822
        private const val MAX_BIND_RETRIES = 20
        private const val BIND_RETRY_DELAY_MS = 100L
    }
}
