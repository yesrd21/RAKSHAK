package com.rakshak

import android.app.Application
import com.rakshak.notifications.CallAlertManager
import com.rakshak.notifications.RakshakNotificationManager
import com.rakshak.service.RakshakForegroundService

/**
 * RakshakApp
 *
 * Application entry point.
 * Initialises notification channels on startup.
 */
class RakshakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RakshakNotificationManager.createNotificationChannels(this)
        CallAlertManager.createCallNotificationChannel(this)
        RakshakForegroundService.start(this)
    }
}
