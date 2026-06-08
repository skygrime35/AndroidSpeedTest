package com.androidspeedtest.widget.domain.usecase

import java.util.Locale

class FormatSpeedTestUseCase {

    fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "—"
        val kbps = bps / 1000.0
        val mbps = kbps / 1000.0
        
        return when {
            mbps >= 1.0 -> String.format(Locale.US, "%.1f Mbps", mbps)
            kbps >= 1.0 -> String.format(Locale.US, "%.0f Kbps", kbps)
            else -> "$bps bps"
        }
    }

    fun formatLatency(latencyMs: Double): String {
        if (latencyMs <= 0.0) return "—"
        return String.format(Locale.US, "%.0f ms", latencyMs)
    }
}
