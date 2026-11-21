package com.musictracker.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.musictracker.MainActivity
import com.musictracker.R

object Notifications {
    private const val CHANNEL_LISTENING = "listening"
    private const val CHANNEL_RESUME = "resume"
    private const val CHANNEL_WEB = "web"
    private const val CHANNEL_DOWNLOADS = "downloads"
    const val ID_DOWNLOADS = 4
    const val ID_WEB = 3
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
            NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of songs being saved to your Music folder"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WEB, "Web on your PC", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while the Music Tracker website is open to your home Wi-Fi"
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

    fun web(context: Context, address: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_WEB)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Music Tracker website is on")
            .setContentText("Open $address on a computer on this Wi-Fi")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context, null))
            .build()
    }

    /** "Downloading Kesariya (2 more waiting)", with a progress bar; or a finished message. */
    fun showDownload(context: Context, title: String, text: String, percent: Int?) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setSilent(true)
            .setOngoing(percent != null)
            .apply { if (percent != null) setProgress(100, percent, percent < 0) }
            .setContentIntent(openApp(context, null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_DOWNLOADS, n) } // no permission: skip quietly
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
