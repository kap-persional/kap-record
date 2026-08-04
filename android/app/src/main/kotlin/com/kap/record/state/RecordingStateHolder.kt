package com.kap.record.state

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.quicksettings.TileService
import com.kap.record.Constants
import com.kap.record.tile.RecordTileService

/**
 * Nguồn sự thật duy nhất về trạng thái ghi hình, dùng chung giữa Service,
 * TileService và các MethodChannel/EventChannel handler — vì cả 3 chạy
 * trong cùng một tiến trình (không khai báo android:process riêng).
 * Trạng thái được lưu cả vào SharedPreferences để Tile vẫn đọc đúng
 * ngay cả khi tiến trình bị hệ thống khởi động lại độc lập.
 */
object RecordingStateHolder {

    interface Listener {
        fun onStateChanged(
            state: RecordingState,
            isPaused: Boolean,
            elapsedMs: Long,
            error: String?,
            audioLikelySilent: Boolean
        )
    }

    @Volatile
    var state: RecordingState = RecordingState.IDLE
        private set

    @Volatile
    var isPaused: Boolean = false
        private set

    /**
     * Cờ cảnh báo (không phải lỗi): trong buổi ghi hiện tại, AudioCapture phát hiện dữ liệu
     * PCM gần như im lặng suốt vài giây đầu — có thể do thiết bị OEM không capture được âm
     * thanh nội bộ dù AudioRecord khởi tạo "thành công". Chỉ mang tính heuristic cảnh báo cho
     * người dùng, KHÔNG huỷ buổi ghi.
     */
    @Volatile
    var audioLikelySilent: Boolean = false
        private set

    private var startElapsedRealtime: Long = 0L
    private var accumulatedMs: Long = 0L
    private val listeners = mutableSetOf<Listener>()
    private var restored = false

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun ensureRestored(context: Context) {
        if (restored) return
        restored = true
        val prefs = prefs(context)
        state = RecordingState.fromName(prefs.getString(Constants.PREF_STATE, null))
        isPaused = prefs.getBoolean(Constants.PREF_IS_PAUSED, false)
        accumulatedMs = prefs.getLong(Constants.PREF_ACCUMULATED_MS, 0L)
        // elapsedRealtime không có ý nghĩa xuyên tiến trình cũ nếu service đã bị hệ thống
        // giết cùng tiến trình; trong trường hợp đó accumulatedMs vẫn là mốc gần nhất đã lưu.
        startElapsedRealtime = 0L
        if (state == RecordingState.RECORDING) {
            startElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    fun currentElapsedMs(): Long {
        return if (state == RecordingState.RECORDING && startElapsedRealtime > 0) {
            accumulatedMs + (SystemClock.elapsedRealtime() - startElapsedRealtime)
        } else {
            accumulatedMs
        }
    }

    fun markArmed(context: Context) {
        state = RecordingState.ARMED
        isPaused = false
        accumulatedMs = 0L
        startElapsedRealtime = 0L
        persistAndNotify(context, null)
    }

    fun markRecordingStarted(context: Context) {
        state = RecordingState.RECORDING
        isPaused = false
        accumulatedMs = 0L
        startElapsedRealtime = SystemClock.elapsedRealtime()
        audioLikelySilent = false
        persistAndNotify(context, null)
    }

    fun markPaused(context: Context) {
        if (state == RecordingState.RECORDING && startElapsedRealtime > 0) {
            accumulatedMs += SystemClock.elapsedRealtime() - startElapsedRealtime
        }
        state = RecordingState.PAUSED
        isPaused = true
        persistAndNotify(context, null)
    }

    fun markResumed(context: Context) {
        state = RecordingState.RECORDING
        isPaused = false
        startElapsedRealtime = SystemClock.elapsedRealtime()
        persistAndNotify(context, null)
    }

    fun markStopping(context: Context) {
        state = RecordingState.STOPPING
        persistAndNotify(context, null)
    }

    /** Đánh dấu buổi ghi hiện tại có vẻ đang ghi audio im lặng — chỉ cảnh báo, không đổi state. */
    fun markAudioLikelySilent(context: Context) {
        if (audioLikelySilent) return
        audioLikelySilent = true
        persistAndNotify(context, null)
    }

    fun reset(context: Context, error: String? = null) {
        state = RecordingState.IDLE
        isPaused = false
        accumulatedMs = 0L
        startElapsedRealtime = 0L
        audioLikelySilent = false
        persistAndNotify(context, error)
    }

    /** Gọi định kỳ (~1 lần/giây) khi đang RECORDING/PAUSED để cập nhật đồng hồ mà không đổi state. */
    fun tick(context: Context) {
        if (state != RecordingState.RECORDING && state != RecordingState.PAUSED) return
        val snapshotElapsed = currentElapsedMs()
        listeners.toList().forEach { it.onStateChanged(state, isPaused, snapshotElapsed, null, audioLikelySilent) }
    }

    private fun persistAndNotify(context: Context, error: String?) {
        prefs(context).edit()
            .putString(Constants.PREF_STATE, state.name)
            .putBoolean(Constants.PREF_IS_PAUSED, isPaused)
            .putLong(Constants.PREF_ACCUMULATED_MS, accumulatedMs)
            .apply()
        val snapshotElapsed = currentElapsedMs()
        listeners.toList().forEach { it.onStateChanged(state, isPaused, snapshotElapsed, error, audioLikelySilent) }
        requestTileRefresh(context)
    }

    private fun requestTileRefresh(context: Context) {
        val appContext = context.applicationContext
        TileService.requestListeningState(
            appContext,
            ComponentName(appContext, RecordTileService::class.java)
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
}
