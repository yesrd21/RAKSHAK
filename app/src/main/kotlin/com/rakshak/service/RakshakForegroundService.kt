package com.rakshak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakshak.R
import com.rakshak.ui.MainActivity

/**
 * RakshakForegroundService
 *
 * A persistent foreground service that keeps Rakshak AI alive in the background.
 *
 * WHY THIS IS NEEDED:
 *   On aggressive battery-saving phones (Xiaomi, Samsung, Realme, OnePlus),
 *   Android kills BroadcastReceiver processes when the app is in the background.
 *   A foreground service with a visible notification prevents this.
 *
 * WHAT IT DOES:
 *   Shows a small persistent notification "Rakshak AI is active".
 *   Keeps the process alive so SmsReceiver and CallReceiver always fire.
 *   Uses FOREGROUND_SERVICE_TYPE_DATA_SYNC — lightweight, no sensors.
 *
 * PRIVACY:
 *   Does NOT access microphone, camera, location, or any sensors.
 *   Only purpose is process persistence.
 */
class RakshakForegroundService : Service() {

    companion object {
        private const val TAG            = "RakshakFgService"
        private const val CHANNEL_ID     = "rakshak_protection_service"
        private const val NOTIF_ID       = 9001

        fun start(context: Context) {
            val intent = Intent(context, RakshakForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Service start requested")
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RakshakForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service running — monitoring active")
        // START_STICKY: if killed by system, restart automatically
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed — will restart via START_STICKY")
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW   // silent — no sound, no vibration
            ).apply {
                description = "Keeps Rakshak AI running in the background"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_body))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)          // cannot be swiped away
            .setSilent(true)           // no sound, no vibration
            .setShowWhen(false)        // don't show timestamp
            .build()
    }
}
