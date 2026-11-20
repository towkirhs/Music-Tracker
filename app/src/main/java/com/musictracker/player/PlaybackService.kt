package com.musictracker.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musictracker.MainActivity
import com.musictracker.net.Http
import com.musictracker.tracking.Heard
import com.musictracker.tracking.PlayTracker
import com.musictracker.tracking.Prefs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.IOException

/**
 * Plays songs in the background with notification, lock-screen and headphone controls.
 * Songs are queued as "musictracker://play?v=<video>&q=<title artist>" and turned into a real
 * audio address only when they start, so a queue of hundreds of library songs costs nothing.
 * Played audio is cached (up to 512 MB) so replays start at once and use no data.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val upstream = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.USER_AGENT)
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            val uri = spec.uri
            if (uri.scheme != SCHEME) {
                spec
            } else {
                val id = uri.getQueryParameter("v")?.ifBlank { null }
                    ?: YouTube.videoFor(uri.getQueryParameter("q").orEmpty())
                    ?: throw IOException("Couldn't find this song on YouTube")
                spec.withUri(Uri.parse(YouTube.audioUrl(id)))
            }
        }
        val dataSource = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.playbackParameters = androidx.media3.common.PlaybackParameters(
            Prefs.playerSpeed(this), Prefs.playerPitch(this),
        )
        player.addListener(Tracker(player))

        val open = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Items arrive from the app without their address; it is kept in the media id.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList(),
                )
            })
            .build()
    }

    /** Logs what the built-in player plays into the library, like any other music app. */
    private inner class Tracker(private val player: ExoPlayer) : Player.Listener {
        private var retried: String? = null

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                val meta = player.currentMediaItem?.mediaMetadata
                val heard = if (player.isPlaying && meta?.title != null) {
                    Heard(
                        meta.title.toString(), meta.artist?.toString().orEmpty(),
                        "played in Music Tracker", "Music Tracker player", "player",
                    )
                } else {
                    null
                }
                PlayTracker.observe(this@PlaybackService, PlayTracker.Source.PLAYER, heard)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Stream addresses expire after a few hours: look the song up again once, then skip it.
            val item = player.currentMediaItem ?: return
            val uri = item.localConfiguration?.uri
            if (uri != null && retried != item.mediaId) {
                retried = item.mediaId
                YouTube.forget(uri.getQueryParameter("v"), uri.getQueryParameter("q"))
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlayTracker.end(PlayTracker.Source.PLAYER)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val SCHEME = "musictracker"

        @Volatile private var simpleCache: SimpleCache? = null

        private fun cache(service: PlaybackService): SimpleCache =
            simpleCache ?: synchronized(this) {
                simpleCache ?: SimpleCache(
                    File(service.cacheDir, "player"),
                    LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
                    StandaloneDatabaseProvider(service),
                ).also { simpleCache = it }
            }

        fun mediaItem(song: Song): MediaItem {
            val uri = Uri.Builder().scheme(SCHEME).authority("play")
                .appendQueryParameter("v", song.videoId.orEmpty())
                .appendQueryParameter("q", song.query)
                .build()
            return MediaItem.Builder()
                .setMediaId(uri.toString())
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(song.thumbnail?.let(Uri::parse))
                        .setExtras(Bundle().apply { putLong("durationMs", song.durationMs) })
                        .build(),
                )
                .build()
        }
    }
}
