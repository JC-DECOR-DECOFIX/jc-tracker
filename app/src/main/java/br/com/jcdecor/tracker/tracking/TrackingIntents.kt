package br.com.jcdecor.tracker.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object TrackingIntents {
    fun start(context: Context) {
        val intent = Intent(context, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun resume(context: Context) {
        val intent = Intent(context, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_RESUME
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
