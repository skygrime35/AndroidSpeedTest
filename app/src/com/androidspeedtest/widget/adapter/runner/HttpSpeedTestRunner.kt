package com.androidspeedtest.widget.adapter.runner

import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState
import com.androidspeedtest.widget.domain.port.SpeedTestListener
import com.androidspeedtest.widget.domain.port.SpeedTestRunner
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpSpeedTestRunner : SpeedTestRunner {

    override fun runTest(
        downloadUrl: String,
        uploadUrl: String,
        pingUrl: String,
        downloadDurationMs: Long,
        uploadDurationMs: Long,
        listener: SpeedTestListener
    ): SpeedTestResult {
        
        // 1. Latency test
        val latencyMs = runPingTest(pingUrl, listener)
        
        // 2. Download speed test
        val maxDownloadSpeedBps = runDownloadTest(downloadUrl, latencyMs, downloadDurationMs, listener)
        
        // 3. Upload speed test
        val maxUploadSpeedBps = runUploadTest(uploadUrl, maxDownloadSpeedBps, latencyMs, uploadDurationMs, listener)

        return SpeedTestResult(
            downloadSpeedMaxBps = maxDownloadSpeedBps,
            uploadSpeedMaxBps = maxUploadSpeedBps,
            latencyMs = latencyMs,
            timestamp = System.currentTimeMillis(),
            state = SpeedTestState.COMPLETED
        )
    }

    private fun runPingTest(pingUrl: String, listener: SpeedTestListener): Double {
        val url = URL(pingUrl)
        val host = url.host
        listener.onStateChanged(SpeedTestState.PINGING, "Measuring latency to $host...")
        
        val pings = mutableListOf<Double>()
        val totalAttempts = 4
        
        try {
            val port = if (url.port != -1) url.port else (if (url.protocol.lowercase() == "https") 443 else 80)
            
            // Resolve DNS once before the loop to exclude DNS resolution time from latency
            val inetAddress = java.net.InetAddress.getByName(host)
            val socketAddress = java.net.InetSocketAddress(inetAddress, port)
            
            for (i in 1..totalAttempts) {
                var socket: java.net.Socket? = null
                try {
                    socket = java.net.Socket()
                    val start = System.nanoTime()
                    socket.connect(socketAddress, 5000)
                    val elapsed = (System.nanoTime() - start) / 1_000_000.0
                    pings.add(elapsed)
                } catch (e: Exception) {
                    // Ignore and try next connection
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
                
                // Wait slightly between pings
                Thread.sleep(150)
            }
        } catch (e: Exception) {
            // Handled if DNS or host resolution fails completely
        }

        if (pings.isEmpty()) {
            throw Exception("Failed to contact latency server ($host).")
        }

        // Discard first ping if we have multiple, to avoid TCP connection warmup skewing results
        val averagedPings = if (pings.size > 1) pings.subList(1, pings.size) else pings
        val avgLatency = averagedPings.average()
        
        listener.onProgress(SpeedTestState.PINGING, 0L, 0L, avgLatency)
        return avgLatency
    }

    private fun runDownloadTest(
        downloadUrl: String,
        avgLatency: Double,
        downloadDurationMs: Long,
        listener: SpeedTestListener
    ): Long {
        listener.onStateChanged(SpeedTestState.DOWNLOADING, "Testing download speed...")
        
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var maxSpeedBps = 0L
        
        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.useCaches = false
            connection.connect()

            val code = connection.responseCode
            if (code < 200 || code >= 400) {
                throw Exception("Download server returned code $code")
            }

            inputStream = connection.inputStream
            val buffer = ByteArray(65536) // 64KB buffer
            val startTime = System.currentTimeMillis()
            var windowStartTime = startTime
            var windowBytes = 0L
            val testTimeLimitMs = downloadDurationMs

            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                windowBytes += read
                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                val windowElapsed = now - windowStartTime

                if (windowElapsed >= 500) {
                    val windowSpeedBps = (windowBytes * 8 * 1000) / windowElapsed
                    if (windowSpeedBps > maxSpeedBps) {
                        maxSpeedBps = windowSpeedBps
                    }
                    listener.onProgress(SpeedTestState.DOWNLOADING, windowSpeedBps, maxSpeedBps, avgLatency)
                    
                    windowBytes = 0L
                    windowStartTime = now
                }

                if (elapsed >= testTimeLimitMs) {
                    break
                }
            }

            // Final check for remaining bytes or fast transfers
            val totalElapsed = System.currentTimeMillis() - startTime
            if (maxSpeedBps == 0L && totalElapsed > 50) {
                maxSpeedBps = (windowBytes * 8 * 1000) / totalElapsed
            }
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }

        return maxSpeedBps
    }

    private fun runUploadTest(
        uploadUrl: String,
        maxDownloadBps: Long,
        avgLatency: Double,
        uploadDurationMs: Long,
        listener: SpeedTestListener
    ): Long {
        listener.onStateChanged(SpeedTestState.UPLOADING, "Testing upload speed...")
        
        var connection: HttpURLConnection? = null
        var outputStream: OutputStream? = null
        var maxSpeedBps = 0L
        
        try {
            val url = URL(uploadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            // Use chunked streaming mode to avoid caching the entire body in memory
            connection.setChunkedStreamingMode(65536)
            connection.useCaches = false
            connection.connect()

            outputStream = connection.outputStream
            val buffer = ByteArray(65536) // 64KB dummy data buffer
            val startTime = System.currentTimeMillis()
            var windowStartTime = startTime
            var windowBytes = 0L
            var totalBytesWritten = 0L
            val testTimeLimitMs = uploadDurationMs

            while (true) {
                outputStream.write(buffer)
                windowBytes += buffer.size
                totalBytesWritten += buffer.size
                
                val now = System.currentTimeMillis()
                val elapsed = now - startTime
                val windowElapsed = now - windowStartTime

                if (windowElapsed >= 500) {
                    val windowSpeedBps = (windowBytes * 8 * 1000) / windowElapsed
                    if (windowSpeedBps > maxSpeedBps) {
                        maxSpeedBps = windowSpeedBps
                    }
                    // Report download speed preserved, upload active
                    listener.onProgress(SpeedTestState.UPLOADING, windowSpeedBps, maxSpeedBps, avgLatency)
                    
                    windowBytes = 0L
                    windowStartTime = now
                }

                if (elapsed >= testTimeLimitMs) {
                    break
                }
            }

            // Final check for remaining bytes or fast transfers
            val totalElapsed = System.currentTimeMillis() - startTime
            if (maxSpeedBps == 0L && totalElapsed > 50) {
                maxSpeedBps = (windowBytes * 8 * 1000) / totalElapsed
            }
            
            outputStream.flush()
            outputStream.close()
            outputStream = null
            
            // Read response code to finalize request (blocks until all bytes are sent and ACKed)
            val code = connection.responseCode
            val finalTimeMs = System.currentTimeMillis() - startTime
            maxSpeedBps = if (finalTimeMs > 50) {
                (totalBytesWritten * 8 * 1000) / finalTimeMs
            } else {
                maxSpeedBps
            }
            
            if (code < 200 || code >= 400) {
                // Not throwing exception here if we successfully wrote the bytes
            }
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }

        return maxSpeedBps
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Android; SpeedTest App)"
    }
}
