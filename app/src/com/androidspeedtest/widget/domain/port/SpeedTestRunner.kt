package com.androidspeedtest.widget.domain.port

import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState

interface SpeedTestListener {
    fun onStateChanged(state: SpeedTestState, message: String)
    fun onProgress(state: SpeedTestState, currentBps: Long, maxBps: Long, latencyMs: Double)
}

interface SpeedTestRunner {
    fun runTest(
        downloadUrl: String,
        uploadUrl: String,
        pingUrl: String,
        listener: SpeedTestListener
    ): SpeedTestResult
}
