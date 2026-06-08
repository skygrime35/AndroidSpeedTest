package com.androidspeedtest.widget.domain.usecase

import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState
import com.androidspeedtest.widget.domain.port.Clock
import com.androidspeedtest.widget.domain.port.SpeedTestListener
import com.androidspeedtest.widget.domain.port.SpeedTestRepository
import com.androidspeedtest.widget.domain.port.SpeedTestRunner

class RunSpeedTestUseCase(
    private val repository: SpeedTestRepository,
    private val runner: SpeedTestRunner,
    private val clock: Clock
) {
    fun execute(listener: SpeedTestListener): SpeedTestResult {
        val (downloadUrl, uploadUrl, pingUrl) = repository.readUrls()
        
        // Notify start
        listener.onStateChanged(SpeedTestState.CONNECTING, "Starting speed test...")
        repository.writeLatest(
            SpeedTestResult(0, 0, 0.0, clock.nowMillis(), SpeedTestState.CONNECTING)
        )

        val result = try {
            val finalResult = runner.runTest(downloadUrl, uploadUrl, pingUrl, object : SpeedTestListener {
                override fun onStateChanged(state: SpeedTestState, message: String) {
                    listener.onStateChanged(state, message)
                }

                override fun onProgress(state: SpeedTestState, currentBps: Long, maxBps: Long, latencyMs: Double) {
                    val partialResult = SpeedTestResult(
                        downloadSpeedMaxBps = if (state == SpeedTestState.DOWNLOADING) maxBps else if (state == SpeedTestState.UPLOADING) repository.readLatest().downloadSpeedMaxBps else 0L,
                        uploadSpeedMaxBps = if (state == SpeedTestState.UPLOADING) maxBps else 0L,
                        latencyMs = latencyMs,
                        timestamp = clock.nowMillis(),
                        state = state
                    )
                    repository.writeLatest(partialResult)
                    listener.onProgress(state, currentBps, maxBps, latencyMs)
                }
            })
            
            // Save final completed state
            val completedResult = finalResult.copy(
                timestamp = clock.nowMillis(),
                state = SpeedTestState.COMPLETED
            )
            repository.writeLatest(completedResult)
            completedResult
        } catch (e: Exception) {
            val errorResult = SpeedTestResult(
                downloadSpeedMaxBps = 0,
                uploadSpeedMaxBps = 0,
                latencyMs = 0.0,
                timestamp = clock.nowMillis(),
                state = SpeedTestState.ERROR,
                errorMessage = e.message ?: "Unknown error"
            )
            repository.writeLatest(errorResult)
            listener.onStateChanged(SpeedTestState.ERROR, e.message ?: "Unknown error")
            errorResult
        }
        
        return result
    }
}
