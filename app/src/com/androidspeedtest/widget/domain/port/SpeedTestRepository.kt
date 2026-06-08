package com.androidspeedtest.widget.domain.port

import com.androidspeedtest.widget.domain.model.SpeedTestResult

interface SpeedTestRepository {
    fun readLatest(): SpeedTestResult
    fun writeLatest(result: SpeedTestResult)
    fun readHistory(): List<SpeedTestResult>
    fun saveUrls(downloadUrl: String, uploadUrl: String, pingUrl: String)
    fun readUrls(): Triple<String, String, String>
}
