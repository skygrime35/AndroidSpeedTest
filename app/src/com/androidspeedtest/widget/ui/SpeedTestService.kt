package com.androidspeedtest.widget.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.androidspeedtest.widget.di.ServiceLocator
import com.androidspeedtest.widget.domain.model.SpeedTestState
import com.androidspeedtest.widget.domain.port.SpeedTestListener

class SpeedTestService : Service() {

    private var testThread: Thread? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (testThread == null || !testThread!!.isAlive) {
            val notification = buildNotification("Initializing speed test...")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            testThread = Thread {
                try {
                    val runUseCase = ServiceLocator.getRunUseCase(this@SpeedTestService)
                    runUseCase.execute(object : SpeedTestListener {
                        override fun onStateChanged(state: SpeedTestState, message: String) {
                            updateNotification(message)
                            sendUpdateBroadcast()
                        }

                        override fun onProgress(
                            state: SpeedTestState, 
                            currentBps: Long, 
                            maxBps: Long, 
                            latencyMs: Double
                        ) {
                            val formatter = ServiceLocator.formatUseCase
                            val latest = ServiceLocator.getRepository(this@SpeedTestService).readLatest()
                            val prefix = "Run ${latest.currentIteration}/${latest.totalIterations}: "
                            val progressMsg = when (state) {
                                SpeedTestState.PINGING -> prefix + "Latency: ${formatter.formatLatency(latencyMs)}"
                                SpeedTestState.DOWNLOADING -> prefix + "Download: ${formatter.formatSpeed(maxBps)}"
                                SpeedTestState.UPLOADING -> prefix + "Upload: ${formatter.formatSpeed(maxBps)}"
                                else -> prefix + "Testing..."
                            }
                            updateNotification(progressMsg)
                            sendUpdateBroadcast()
                        }
                    })
                } catch (_: Exception) {
                } finally {
                    stopForeground(true)
                    stopSelf()
                    // Send final broadcast to update widgets and activities
                    sendUpdateBroadcast()
                }
            }
            testThread!!.start()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        testThread?.interrupt()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Speed Test Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active speed tests."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Speed Test Active")
            .setContentText(text)
            // Use built-in system drawable to guarantee successful compile without extra resources
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendUpdateBroadcast() {
        val widgetIntent = Intent(this, SpeedTestWidget::class.java).apply {
            action = SpeedTestWidget.ACTION_UPDATE_WIDGET
        }
        sendBroadcast(widgetIntent)

        val appIntent = Intent(ACTION_APP_UPDATE)
        sendBroadcast(appIntent)
    }

    companion object {
        private const val CHANNEL_ID = "speed_test_running_channel"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_APP_UPDATE = "com.androidspeedtest.widget.ACTION_APP_UPDATE"
    }
}
