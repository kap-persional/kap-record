package com.kap.record.state

enum class RecordingState {
    IDLE,
    ARMED,
    RECORDING,
    PAUSED,
    STOPPING;

    companion object {
        fun fromName(name: String?): RecordingState =
            entries.firstOrNull { it.name == name } ?: IDLE
    }
}
