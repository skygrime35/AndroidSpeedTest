package com.androidspeedtest.widget.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.androidspeedtest.widget.R
import com.androidspeedtest.widget.di.ServiceLocator
import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetRenderer {

    fun render(context: Context, result: SpeedTestResult): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_speedtest)
        val formatter = ServiceLocator.formatUseCase

        // 1. Format Speeds and Latency values
        val pingStr = if (result.state == SpeedTestState.IDLE) "—" else formatter.formatLatency(result.latencyMs)
        val downloadStr = if (result.state == SpeedTestState.IDLE || result.state == SpeedTestState.PINGING || result.state == SpeedTestState.CONNECTING) "—" else formatter.formatSpeed(result.downloadSpeedMaxBps)
        val uploadStr = if (result.state != SpeedTestState.UPLOADING && result.state != SpeedTestState.COMPLETED) "—" else formatter.formatSpeed(result.uploadSpeedMaxBps)

        views.setTextViewText(R.id.widget_ping_value, pingStr)
        views.setTextViewText(R.id.widget_download_value, downloadStr)
        views.setTextViewText(R.id.widget_upload_value, uploadStr)

        // 2. Set Progress Bars (scale: 0 to 150 Mbps)
        val maxScaleMbps = 150.0
        val downloadProgress = if (result.state == SpeedTestState.IDLE) 0 else {
            val mbps = result.downloadSpeedMaxBps / 1_000_000.0
            ((mbps / maxScaleMbps) * 100).toInt().coerceIn(0, 100)
        }
        val uploadProgress = if (result.state == SpeedTestState.IDLE) 0 else {
            val mbps = result.uploadSpeedMaxBps / 1_000_000.0
            ((mbps / maxScaleMbps) * 100).toInt().coerceIn(0, 100)
        }

        views.setProgressBar(R.id.widget_download_progress, 100, downloadProgress, false)
        views.setProgressBar(R.id.widget_upload_progress, 100, uploadProgress, false)

        // 3. Set Status Message
        val statusText = when (result.state) {
            SpeedTestState.IDLE -> context.getString(R.string.label_never_tested)
            SpeedTestState.CONNECTING -> context.getString(R.string.label_testing, "Connecting...")
            SpeedTestState.PINGING -> context.getString(R.string.label_testing, "Ping...")
            SpeedTestState.DOWNLOADING -> {
                val current = formatter.formatSpeed(result.downloadSpeedMaxBps)
                context.getString(R.string.label_testing, "Download ($current)")
            }
            SpeedTestState.UPLOADING -> {
                val current = formatter.formatSpeed(result.uploadSpeedMaxBps)
                context.getString(R.string.label_testing, "Upload ($current)")
            }
            SpeedTestState.COMPLETED -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(result.timestamp))
                context.getString(R.string.label_last_test, timeStr)
            }
            SpeedTestState.ERROR -> {
                val errMsg = result.errorMessage ?: "Unknown error"
                "Failed: $errMsg. Tap to retry."
            }
        }
        views.setTextViewText(R.id.widget_status, statusText)

        // 4. Attach Click PendingIntent to the entire widget root
        val intent = Intent(context, SpeedTestWidget::class.java).apply {
            action = SpeedTestWidget.ACTION_TRIGGER_TEST
        }
        
        // PendingIntent flags: MUST set IMMUTABLE or MUTABLE. Using FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }
}
