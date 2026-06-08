package com.androidspeedtest.widget.domain.model

data class SpeedTestResult(
    val downloadSpeedMaxBps: Long,
    val uploadSpeedMaxBps: Long,
    val latencyMs: Double,
    val timestamp: Long,
    val state: SpeedTestState,
    val currentIteration: Int = 1,
    val totalIterations: Int = 3,
    val completedDownloads: List<Long> = emptyList(),
    val completedUploads: List<Long> = emptyList(),
    val completedLatencies: List<Double> = emptyList(),
    val errorMessage: String? = null
) {
    companion object {
        fun idle() = SpeedTestResult(
            downloadSpeedMaxBps = 0,
            uploadSpeedMaxBps = 0,
            latencyMs = 0.0,
            timestamp = 0,
            state = SpeedTestState.IDLE,
            currentIteration = 1,
            totalIterations = 3
        )
    }
}
