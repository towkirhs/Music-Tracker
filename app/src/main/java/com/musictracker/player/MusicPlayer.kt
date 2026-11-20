package com.musictracker.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the player screens show. */
data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val error: String? = null,
)

/**
 * The app's handle on [PlaybackService]: connects once, then plays, queues and controls songs.
 * Main thread only.
 */
object MusicPlayer {

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var sleepAt = 0L

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _sleepTimer = MutableStateFlow(0L)
    /** When the sleep timer pauses playback (wall-clock ms), or 0 when off. */
    val sleepTimer: StateFlow<Long> = _sleepTimer

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(context: Context, action: (MediaController) -> Unit) {
        controller?.let { return action(it) }
        pending += action
        connect(context)
    }

    /** Current position in ms; read often by the player screen. */
    fun position(): Long = controller?.currentPosition ?: 0L

    fun play(context: Context, songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController(context) { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map(PlaybackService::mediaItem), startIndex.coerceIn(0, songs.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(context: Context, song: Song) = withController(context) { c ->
        if (c.mediaItemCount == 0) {
            play(context, listOf(song))
        } else {
            c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), PlaybackService.mediaItem(song))
        }
    }

    fun addToQueue(context: Context, song: Song) = withController(context) { c ->
        if (c.mediaItemCount == 0) play(context, listOf(song)) else c.addMediaItem(PlaybackService.mediaItem(song))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun skipTo(index: Int) = controller?.let { it.seekTo(index, 0); it.play() }
    fun removeAt(index: Int) = controller?.removeMediaItem(index)

    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Tempo and pitch, kept for next time. */
    fun setSpeedPitch(context: Context, speed: Float, pitch: Float) {
        Prefs.setPlayerSpeedPitch(context, speed, pitch)
        controller?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleep = Runnable {
        controller?.pause()
        setSleepTimer(0)
    }

    /** Pauses after [minutes], even with the screen off; 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleep)
        sleepAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        if (minutes > 0) handler.postDelayed(sleep, minutes * 60_000L)
        _sleepTimer.value = sleepAt
    }

    private fun refresh() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        val duration = c.duration.takeIf { it > 0 } ?: (c.currentMediaItem?.toSong()?.durationMs ?: 0L)
        _state.value = PlayerState(
            current = c.currentMediaItem?.toSong(),
            isPlaying = c.isPlaying,
            isLoading = c.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            queue = items,
            index = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
            error = c.playerError?.let { "Couldn't play this song: " + (it.cause?.message ?: it.errorCodeName) },
        )
    }

    private fun MediaItem.toSong(): Song {
        val m = mediaMetadata
        val v = runCatching { android.net.Uri.parse(mediaId).getQueryParameter("v") }.getOrNull()?.ifBlank { null }
        return Song(
            videoId = v,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString().orEmpty(),
            thumbnail = m.artworkUri?.toString(),
            durationMs = m.extras?.getLong("durationMs") ?: 0L,
        )
    }
}
