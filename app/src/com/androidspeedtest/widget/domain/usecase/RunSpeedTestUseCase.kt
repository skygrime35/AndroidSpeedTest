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
        
        val totalIterations = 3
        val completedDownloads = mutableListOf<Long>()
        val completedUploads = mutableListOf<Long>()
        val completedLatencies = mutableListOf<Double>()

        var latestResult = SpeedTestResult.idle()

        try {
            for (iteration in 1..totalIterations) {
                // Notify transition to new iteration
                listener.onStateChanged(SpeedTestState.CONNECTING, "Starting run $iteration of $totalIterations...")
                
                var currentRunMaxDownload = 0L
                var currentRunMaxUpload = 0L
                var currentRunLatency = 0.0

                repository.writeLatest(
                    SpeedTestResult(
                        downloadSpeedMaxBps = 0L,
                        uploadSpeedMaxBps = 0L,
                        latencyMs = 0.0,
                        timestamp = clock.nowMillis(),
                        state = SpeedTestState.CONNECTING,
                        currentIteration = iteration,
                        totalIterations = totalIterations,
                        completedDownloads = completedDownloads.toList(),
                        completedUploads = completedUploads.toList(),
                        completedLatencies = completedLatencies.toList()
                    )
                )

                // Execute the single run with 4 seconds download limit and 8 seconds upload limit
                val runResult = runner.runTest(
                    downloadUrl, 
                    uploadUrl, 
                    pingUrl, 
                    4000L, 
                    8000L, 
                    object : SpeedTestListener {
                        override fun onStateChanged(state: SpeedTestState, message: String) {
                            listener.onStateChanged(state, "Run $iteration/$totalIterations: $message")
                        }

                        override fun onProgress(
                            state: SpeedTestState, 
                            currentBps: Long, 
                            maxBps: Long, 
                            latencyMs: Double
                        ) {
                            if (state == SpeedTestState.PINGING) {
                                currentRunLatency = latencyMs
                            } else if (state == SpeedTestState.DOWNLOADING) {
                                currentRunMaxDownload = maxBps
                            } else if (state == SpeedTestState.UPLOADING) {
                                currentRunMaxUpload = maxBps
                            }

                            val partialResult = SpeedTestResult(
                                downloadSpeedMaxBps = currentRunMaxDownload,
                                uploadSpeedMaxBps = currentRunMaxUpload,
                                latencyMs = currentRunLatency,
                                timestamp = clock.nowMillis(),
                                state = state,
                                currentIteration = iteration,
                                totalIterations = totalIterations,
                                completedDownloads = completedDownloads.toList(),
                                completedUploads = completedUploads.toList(),
                                completedLatencies = completedLatencies.toList()
                            )
                            repository.writeLatest(partialResult)
                            listener.onProgress(state, currentBps, maxBps, latencyMs)
                        }
                    }
                )

                // Collect results from this run
                completedDownloads.add(runResult.downloadSpeedMaxBps)
                completedUploads.add(runResult.uploadSpeedMaxBps)
                completedLatencies.add(runResult.latencyMs)
            }

            // All runs completed successfully. Compute averages.
            val avgDownload = if (completedDownloads.isNotEmpty()) completedDownloads.average().toLong() else 0L
            val avgUpload = if (completedUploads.isNotEmpty()) completedUploads.average().toLong() else 0L
            // Use the first latency measurement (quiet network) instead of averaging it with bufferbloat-inflated runs
            val avgLatency = if (completedLatencies.isNotEmpty()) completedLatencies.first() else 0.0

            val completedResult = SpeedTestResult(
                downloadSpeedMaxBps = avgDownload,
                uploadSpeedMaxBps = avgUpload,
                latencyMs = avgLatency,
                timestamp = clock.nowMillis(),
                state = SpeedTestState.COMPLETED,
                currentIteration = totalIterations,
                totalIterations = totalIterations,
                completedDownloads = completedDownloads.toList(),
                completedUploads = completedUploads.toList(),
                completedLatencies = completedLatencies.toList()
            )
            repository.writeLatest(completedResult)
            listener.onStateChanged(SpeedTestState.COMPLETED, "Test completed.")
            latestResult = completedResult

        } catch (e: Exception) {
            val errorResult = SpeedTestResult(
                downloadSpeedMaxBps = 0L,
                uploadSpeedMaxBps = 0L,
                latencyMs = 0.0,
                timestamp = clock.nowMillis(),
                state = SpeedTestState.ERROR,
                currentIteration = completedDownloads.size + 1,
                totalIterations = totalIterations,
                completedDownloads = completedDownloads.toList(),
                completedUploads = completedUploads.toList(),
                completedLatencies = completedLatencies.toList(),
                errorMessage = e.message ?: "Unknown error"
            )
            repository.writeLatest(errorResult)
            listener.onStateChanged(SpeedTestState.ERROR, e.message ?: "Unknown error")
            latestResult = errorResult
        }

        return latestResult
    }
}
