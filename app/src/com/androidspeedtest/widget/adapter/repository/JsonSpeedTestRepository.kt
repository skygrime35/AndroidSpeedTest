package com.androidspeedtest.widget.adapter.repository

import android.content.Context
import android.os.Environment
import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState
import com.androidspeedtest.widget.domain.port.SpeedTestRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

class JsonSpeedTestRepository(private val context: Context) : SpeedTestRepository {

    private val prefs = context.getSharedPreferences("SpeedTestPrefs", Context.MODE_PRIVATE)

    override fun readLatest(): SpeedTestResult {
        val jsonStr = prefs.getString(KEY_LATEST, null) ?: return SpeedTestResult.idle()
        return try {
            parseResult(JSONObject(jsonStr))
        } catch (_: Exception) {
            SpeedTestResult.idle()
        }
    }

    override fun writeLatest(result: SpeedTestResult) {
        val jsonObj = serializeResult(result)
        prefs.edit().putString(KEY_LATEST, jsonObj.toString()).apply()

        // Append to history if it is a completed run
        if (result.state == SpeedTestState.COMPLETED) {
            addToHistory(result)
        }

        // Attempt to write to public storage
        writeToPublicFile(jsonObj)
    }

    override fun readHistory(): List<SpeedTestResult> {
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<SpeedTestResult>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(parseResult(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {}
        // Return reverse history (newest first)
        return list.sortedByDescending { it.timestamp }
    }

    override fun saveUrls(downloadUrl: String, uploadUrl: String, pingUrl: String) {
        prefs.edit()
            .putString(KEY_DOWNLOAD_URL, downloadUrl)
            .putString(KEY_UPLOAD_URL, uploadUrl)
            .putString(KEY_PING_URL, pingUrl)
            .apply()
    }

    override fun readUrls(): Triple<String, String, String> {
        val download = prefs.getString(KEY_DOWNLOAD_URL, DEFAULT_DOWNLOAD_URL) ?: DEFAULT_DOWNLOAD_URL
        val upload = prefs.getString(KEY_UPLOAD_URL, DEFAULT_UPLOAD_URL) ?: DEFAULT_UPLOAD_URL
        val ping = prefs.getString(KEY_PING_URL, DEFAULT_PING_URL) ?: DEFAULT_PING_URL
        return Triple(download, upload, ping)
    }

    private fun addToHistory(result: SpeedTestResult) {
        val history = readHistory().toMutableList()
        history.add(0, result) // Add to top
        
        // Limit to last 50 tests
        val trimmed = if (history.size > 50) history.subList(0, 50) else history
        
        val arr = JSONArray()
        for (item in trimmed) {
            arr.put(serializeResult(item))
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun writeToPublicFile(jsonObj: JSONObject) {
        try {
            // Try /sdcard/Download first
            var downloadDir = File("/sdcard/Download")
            if (!downloadDir.exists() || !downloadDir.isDirectory) {
                downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            if (downloadDir.exists() || downloadDir.mkdirs()) {
                val file = File(downloadDir, "speedtest_results.json")
                FileWriter(file).use { writer ->
                    writer.write(jsonObj.toString(2))
                }
            }
        } catch (_: Exception) {
            // Fail silently if permission is not granted yet
        }
    }

    private fun serializeResult(result: SpeedTestResult): JSONObject {
        return JSONObject().apply {
            put("download_speed_max_bps", result.downloadSpeedMaxBps)
            put("upload_speed_max_bps", result.uploadSpeedMaxBps)
            put("latency_ms", result.latencyMs)
            put("timestamp", result.timestamp)
            put("state", result.state.name)
            put("error_message", result.errorMessage ?: JSONObject.NULL)
        }
    }

    private fun parseResult(json: JSONObject): SpeedTestResult {
        return SpeedTestResult(
            downloadSpeedMaxBps = json.getLong("download_speed_max_bps"),
            uploadSpeedMaxBps = json.getLong("upload_speed_max_bps"),
            latencyMs = json.getDouble("latency_ms"),
            timestamp = json.getLong("timestamp"),
            state = SpeedTestState.valueOf(json.getString("state")),
            errorMessage = if (json.isNull("error_message")) null else json.getString("error_message")
        )
    }

    companion object {
        private const val KEY_LATEST = "latest_result"
        private const val KEY_HISTORY = "history_results"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_UPLOAD_URL = "upload_url"
        private const val KEY_PING_URL = "ping_url"

        private const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=10485760" // 10MB
        private const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"
        private const val DEFAULT_PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
    }
}
