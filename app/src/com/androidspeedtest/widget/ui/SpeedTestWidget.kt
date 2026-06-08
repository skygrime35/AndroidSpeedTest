package com.androidspeedtest.widget.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.androidspeedtest.widget.di.ServiceLocator
import com.androidspeedtest.widget.domain.model.SpeedTestResult
import com.androidspeedtest.widget.domain.model.SpeedTestState

class SpeedTestWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repo = ServiceLocator.getRepository(context)
        val latest = repo.readLatest()
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, WidgetRenderer.render(context, latest))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        
        if (action == ACTION_TRIGGER_TEST) {
            // 1. Start background Foreground Service
            val serviceIntent = Intent(context, SpeedTestService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. Instantly update UI to Connecting state
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SpeedTestWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            
            val connectingResult = SpeedTestResult(
                downloadSpeedMaxBps = 0L,
                uploadSpeedMaxBps = 0L,
                latencyMs = 0.0,
                timestamp = System.currentTimeMillis(),
                state = SpeedTestState.CONNECTING
            )
            for (id in ids) {
                appWidgetManager.updateAppWidget(id, WidgetRenderer.render(context, connectingResult))
            }
            
        } else if (action == ACTION_UPDATE_WIDGET) {
            // 3. Receive progress tick or completion updates from the Service
            val repo = ServiceLocator.getRepository(context)
            val latest = repo.readLatest()
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SpeedTestWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            for (id in ids) {
                appWidgetManager.updateAppWidget(id, WidgetRenderer.render(context, latest))
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_TEST = "com.androidspeedtest.widget.ACTION_TRIGGER_TEST"
        const val ACTION_UPDATE_WIDGET = "com.androidspeedtest.widget.ACTION_UPDATE_WIDGET"
    }
}
