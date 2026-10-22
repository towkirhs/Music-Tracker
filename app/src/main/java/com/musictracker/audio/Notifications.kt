package com.musictracker.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.musictracker.MainActivity
import com.musictracker.R

object Notifications {
    private const val CHANNEL_LISTENING = "listening"
    private const val CHANNEL_RESUME = "resume"
    const val ID_LISTENING = 1
    private const val ID_RESUME = 2
    const val ACTION_RESUME_AUDIO = "com.musictracker.RESUME_AUDIO"

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, "Listening", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while Music Tracker can hear songs for lyrics recognition"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESUME, "Resume listening", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Asks you to allow audio again after the screen locks"
                setShowBadge(false)
            },
        )
    }

    private fun openApp(context: Context, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action ?: Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, if (action == null) 0 else 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun listening(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Music Tracker is listening")
            .setContentText("Recognising songs that have no name on screen")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context, null))
            .build()
    }

    fun showResume(context: Context) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_RESUME)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Tap to keep recognising lyrics")
            .setContentText("Android paused audio when the screen locked. Song names on screen are still logged.")
            .setAutoCancel(true)
            .setContentIntent(openApp(context, ACTION_RESUME_AUDIO))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_RESUME, n) }
    }

    fun clearResume(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_RESUME)
    }
}
