package id.smartkids.screenguard.screen_protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import id.smartkids.screenguard.MainActivity

class ScreenGuardForegroundService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "smart_kids_guard_channel"
        private const val NOTIFICATION_ID = 2026

        fun startService(context: Context) {
            val intent = Intent(context, ScreenGuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScreenGuardForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildPrivacyIndicatorNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Kids Screen Guard Active Indicator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan indikator bahwa pelindung mata & postur anak sedang aktif secara lokal."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPrivacyIndicatorNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Kids Screen Guard Aktif")
            .setContentText("Indikator Privasi: Kamera memantau jarak & postur secara lokal (tanpa merekam video).")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
