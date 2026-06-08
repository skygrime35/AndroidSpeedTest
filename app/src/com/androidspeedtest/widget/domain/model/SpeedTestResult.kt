package com.androidspeedtest.widget.domain.model

data class SpeedTestResult(
    val downloadSpeedMaxBps: Long,
    val uploadSpeedMaxBps: Long,
    val latencyMs: Double,
    val timestamp: Long,
    val state: SpeedTestState,
    val errorMessage: String? = null
) {
    companion object {
        fun idle() = SpeedTestResult(
            downloadSpeedMaxBps = 0,
            uploadSpeedMaxBps = 0,
            latencyMs = 0.0,
            timestamp = 0,
            state = SpeedTestState.IDLE
        )
    }
}
