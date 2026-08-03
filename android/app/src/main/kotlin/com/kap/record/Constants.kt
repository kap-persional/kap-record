package com.kap.record

object Constants {
    const val METHOD_CHANNEL = "com.kap.record/recorder"
    const val EVENT_CHANNEL = "com.kap.record/recorder_events"

    // Method names (Dart -> native)
    const val METHOD_REQUEST_PROJECTION_CONSENT = "requestProjectionConsent"
    const val METHOD_START_RECORDING = "startRecording"
    const val METHOD_PAUSE_RECORDING = "pauseRecording"
    const val METHOD_RESUME_RECORDING = "resumeRecording"
    const val METHOD_STOP_RECORDING = "stopRecording"
    const val METHOD_CANCEL_ARMED = "cancelArmed"
    const val METHOD_PICK_SAVE_FOLDER = "pickSaveFolder"
    const val METHOD_GET_CURRENT_STATE = "getCurrentState"

    // Service actions dạng Intent — dùng cho các nơi KHÔNG bind trực tiếp vào Service
    // (nút bấm trên Notification, Quick Settings Tile). START/STOP thật sự khởi tạo qua
    // bindService từ RecorderChannelHandler để có thể trả kết quả/lỗi về cho Dart.
    const val ACTION_ARM = "com.kap.record.action.ARM"
    const val ACTION_PAUSE = "com.kap.record.action.PAUSE"
    const val ACTION_RESUME = "com.kap.record.action.RESUME"
    const val ACTION_STOP = "com.kap.record.action.STOP"
    const val ACTION_CANCEL = "com.kap.record.action.CANCEL"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"

    const val PREFS_NAME = "kap_record_state"
    const val PREF_STATE = "pref_state"
    const val PREF_IS_PAUSED = "pref_is_paused"
    const val PREF_START_ELAPSED_REALTIME = "pref_start_elapsed_realtime"
    const val PREF_ACCUMULATED_MS = "pref_accumulated_ms"

    const val NOTIFICATION_CHANNEL_ID = "kap_record_recording"
    const val NOTIFICATION_ID = 4201

    const val MOVIES_RELATIVE_PATH = "Movies/KapRecord"
    const val MIME_TYPE_MP4 = "video/mp4"
}
