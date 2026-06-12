package com.androidspeedtest.widget.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.androidspeedtest.widget.R
import com.androidspeedtest.widget.di.ServiceLocator
import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var appStatus: TextView
    private lateinit var appPingVal: TextView
    private lateinit var appDownloadVal: TextView
    private lateinit var appUploadVal: TextView
    private lateinit var btnRun: Button
    private lateinit var appPingLoading: android.widget.ProgressBar
    private lateinit var appDownloadLoading: android.widget.ProgressBar
    private lateinit var appUploadLoading: android.widget.ProgressBar
    
    private lateinit var editDownloadUrl: EditText
    private lateinit var editUploadUrl: EditText
    private lateinit var radioGroupPing: android.widget.RadioGroup
    private lateinit var radioPingCloudflare: android.widget.RadioButton
    private lateinit var radioPingGoogle: android.widget.RadioButton
    private lateinit var btnSaveSettings: Button
    private lateinit var editIterations: EditText
    private lateinit var btnClearHistory: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SpeedTestService.ACTION_APP_UPDATE) {
                refreshUi()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI Views
        appStatus = findViewById(R.id.app_status)
        appPingVal = findViewById(R.id.app_ping_val)
        appDownloadVal = findViewById(R.id.app_download_val)
        appUploadVal = findViewById(R.id.app_upload_val)
        btnRun = findViewById(R.id.btn_run)
        appPingLoading = findViewById(R.id.app_ping_loading)
        appDownloadLoading = findViewById(R.id.app_download_loading)
        appUploadLoading = findViewById(R.id.app_upload_loading)
        
        editDownloadUrl = findViewById(R.id.edit_download_url)
        editUploadUrl = findViewById(R.id.edit_upload_url)
        radioGroupPing = findViewById(R.id.radio_group_ping)
        radioPingCloudflare = findViewById(R.id.radio_ping_cloudflare)
        radioPingGoogle = findViewById(R.id.radio_ping_google)
        btnSaveSettings = findViewById(R.id.btn_save_settings)
        editIterations = findViewById(R.id.edit_iterations)
        btnClearHistory = findViewById(R.id.btn_clear_history)

        // Load URLs into Settings fields
        val repo = ServiceLocator.getRepository(this)
        val (downloadUrl, uploadUrl, pingUrl) = repo.readUrls()
        editDownloadUrl.setText(downloadUrl)
        editUploadUrl.setText(uploadUrl)
        if (pingUrl.contains("google")) {
            radioPingGoogle.isChecked = true
        } else {
            radioPingCloudflare.isChecked = true
        }
        editIterations.setText(repo.readIterations().toString())

        // Click Handlers
        btnRun.setOnClickListener {
            triggerSpeedTest()
        }

        btnSaveSettings.setOnClickListener {
            val down = editDownloadUrl.text.toString().trim()
            val up = editUploadUrl.text.toString().trim()
            val ping = if (radioPingGoogle.isChecked) {
                "https://dns.google"
            } else {
                "https://speed.cloudflare.com/__down?bytes=0"
            }
            val iterationsStr = editIterations.text.toString().trim()
            val iterations = iterationsStr.toIntOrNull() ?: 3
            
            if (down.isNotEmpty() && up.isNotEmpty() && iterations in 1..10) {
                repo.saveUrls(down, up, ping)
                repo.saveIterations(iterations)
                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
                refreshUi()
            } else {
                Toast.makeText(this, "Please check all inputs (iterations must be 1-10)", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearHistory.setOnClickListener {
            repo.clearHistory()
            Toast.makeText(this, "History cleared!", Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        // Request permissions
        checkAndRequestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Register receiver for service updates
        val filter = IntentFilter(SpeedTestService.ACTION_APP_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        refreshUi()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    private fun triggerSpeedTest() {
        val serviceIntent = Intent(this, SpeedTestService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Visual intermediate state
        appStatus.text = "Initializing test..."
        btnRun.isEnabled = false
    }

    private fun refreshUi() {
        val repo = ServiceLocator.getRepository(this)
        val latest = repo.readLatest()
        val formatter = ServiceLocator.formatUseCase

        // 1. Update live panel values
        appPingVal.text = formatter.formatLatency(latest.latencyMs)
        appDownloadVal.text = formatter.formatSpeed(latest.downloadSpeedMaxBps)
        appUploadVal.text = formatter.formatSpeed(latest.uploadSpeedMaxBps)

        appPingLoading.visibility = if (latest.state == SpeedTestState.PINGING) android.view.View.VISIBLE else android.view.View.GONE
        appDownloadLoading.visibility = if (latest.state == SpeedTestState.DOWNLOADING) android.view.View.VISIBLE else android.view.View.GONE
        appUploadLoading.visibility = if (latest.state == SpeedTestState.UPLOADING) android.view.View.VISIBLE else android.view.View.GONE

        // 2. Update status label and run button
        btnRun.isEnabled = (latest.state == SpeedTestState.IDLE || latest.state == SpeedTestState.COMPLETED || latest.state == SpeedTestState.ERROR)
        
        val runPrefix = "Run ${latest.currentIteration}/${latest.totalIterations}: "
        appStatus.text = when (latest.state) {
            SpeedTestState.IDLE -> "System Idle"
            SpeedTestState.CONNECTING -> runPrefix + "Connecting..."
            SpeedTestState.PINGING -> runPrefix + "Measuring Ping..."
            SpeedTestState.DOWNLOADING -> runPrefix + "Downloading... (${formatter.formatSpeed(latest.downloadSpeedMaxBps)})"
            SpeedTestState.UPLOADING -> runPrefix + "Uploading... (${formatter.formatSpeed(latest.uploadSpeedMaxBps)})"
            SpeedTestState.COMPLETED -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(latest.timestamp))
                "Last test (Avg of ${latest.totalIterations} runs) completed at $timeStr"
            }
            SpeedTestState.ERROR -> "Test failed (Run ${latest.currentIteration}/${latest.totalIterations}): ${latest.errorMessage ?: "Unknown error"}"
        }

        // 3. Refresh test history list
        refreshHistory()
    }

    private fun refreshHistory() {
        val container = findViewById<LinearLayout>(R.id.history_container)
        container.removeAllViews()

        val repo = ServiceLocator.getRepository(this)
        val history = repo.readHistory()
        val formatter = ServiceLocator.formatUseCase

        if (history.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No history records yet."
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            container.addView(emptyText)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        val density = resources.displayMetrics.density

        for (item in history) {
            // Programmatic Card layout matching widget_bg
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (12 * density).toInt()
                setPadding(padding, padding, padding, padding)
                
                val margin = (8 * density).toInt()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, margin)
                }
                layoutParams = lp
                background = resources.getDrawable(R.drawable.widget_bg)
            }

            // Row 1: Timestamp
            val dateText = TextView(this).apply {
                text = dateFormat.format(Date(item.timestamp)) + " • Avg of ${item.totalIterations} runs"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(dateText)

            // Row 2: Speeds Grid
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
                layoutParams = lp
            }

            // Latency
            val pingText = TextView(this).apply {
                text = "Ping: ${formatter.formatLatency(item.latencyMs)}"
                setTextColor(resources.getColor(R.color.ping_yellow))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            grid.addView(pingText)

            // Download
            val downText = TextView(this).apply {
                text = "Down: ${formatter.formatSpeed(item.downloadSpeedMaxBps)}"
                setTextColor(resources.getColor(R.color.download_blue))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            grid.addView(downText)

            // Upload
            val upText = TextView(this).apply {
                text = "Up: ${formatter.formatSpeed(item.uploadSpeedMaxBps)}"
                setTextColor(resources.getColor(R.color.upload_magenta))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            grid.addView(upText)

            card.addView(grid)
            container.addView(card)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Notification permission for API 33+ (Android 13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permissions for older APIs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        // Manage files permission for Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3003
    }
}
