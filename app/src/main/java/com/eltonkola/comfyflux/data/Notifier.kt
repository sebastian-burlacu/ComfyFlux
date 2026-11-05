package com.eltonkola.comfyflux.data

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eltonkola.comfyflux.MainActivity
import com.eltonkola.comfyflux.R

/** Local notifications for generations that finish while the app is not in front. */
class Notifier(private val context: Application) {

    companion object {
        private const val CHANNEL_ID = "generation"
    }

    private val manager = NotificationManagerCompat.from(context)
    private var nextId = 1000

    fun generationFinished(foreground: Boolean, success: Boolean, detail: String?) {
        if (foreground) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (success) "Generation finished" else "Generation failed"
        val text = if (success) {
            "Your images are ready - open the app to view them"
        } else {
            detail?.take(200) ?: "unknown error"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(nextId++, notification)
        } catch (e: SecurityException) {
            // Notification permission revoked; the result is still visible in-app.
        }
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Generation",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Finished or failed image generations"
            }
        )
    }
}
