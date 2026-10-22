package com.musictracker.tracking

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * Reads what music apps (Spotify, YouTube Music, local players) say is playing.
 * Video apps are skipped here because the label reader handles them, and podcast or
 * audiobook apps are skipped entirely. Anything longer than 15 minutes is treated as
 * a podcast or mix and ignored.
 */
class NowPlayingListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = ComponentName(this, NowPlayingListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            watch(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        PlayTracker.end(PlayTracker.Source.MEDIA)
        super.onListenerDisconnected()
    }

    private fun watch(controllers: List<MediaController>) {
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        for (controller in controllers) {
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
                override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
                override fun onSessionDestroyed() = evaluate()
            }
            controller.registerCallback(callback)
            callbacks[controller] = callback
        }
        evaluate()
    }

    private fun evaluate() {
        val heard = callbacks.keys.firstNotNullOfOrNull { heardFrom(it) }
        PlayTracker.observe(this, PlayTracker.Source.MEDIA, heard)
    }

    private fun heardFrom(controller: MediaController): Heard? {
        val pkg = controller.packageName ?: return null
        if (pkg in LabelParser.WATCHED || pkg in SKIPPED_APPS || pkg == packageName) return null
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return null
        val meta = controller.metadata ?: return null
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (duration > 15 * 60_000L) return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        if (title.isBlank()) return null
        return Heard(title.trim(), artist.trim(), "$title - $artist", appLabel(pkg), "now-playing")
    }

    private fun appLabel(pkg: String): String = MUSIC_APPS[pkg] ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private val MUSIC_APPS = mapOf(
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.apple.android.music" to "Apple Music",
            "com.amazon.mp3" to "Amazon Music",
            "com.jio.media.jiobeats" to "JioSaavn",
            "com.gaana" to "Gaana",
            "com.wynk.music" to "Wynk",
        )

        /** Podcast, audiobook, radio-talk and call apps: never counted. */
        val SKIPPED_APPS = setOf(
            "au.com.shiftyjelly.pocketcasts",
            "fm.castbox.audiobook.radio.podcast",
            "com.google.android.apps.podcasts",
            "com.audible.application",
            "com.storytel",
            "app.podcast.cosmos",
            "com.bambuna.podcastaddict",
            "com.google.android.apps.youtube.kids",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.dialer",
        )
    }
}
