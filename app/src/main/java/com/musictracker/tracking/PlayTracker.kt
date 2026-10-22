package com.musictracker.tracking

import android.content.Context
import android.os.SystemClock
import com.musictracker.data.Repo
import com.musictracker.data.SongText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** What a detector currently hears. */
data class Heard(
    val title: String,
    val artist: String,
    val labelRaw: String,
    val sourceApp: String,
    val detector: String,
)

/**
 * Turns a stream of "now I hear X" observations into plays.
 *
 * Rules:
 * - A play is saved once the same song has been heard for 10 seconds; shorter clips are never counted.
 * - Hearing the same song again within 1 minute (scrolling back to a reel) continues the same play.
 * - Each channel (short videos, music apps) is tracked separately, so both can run at once.
 *
 * Called on the main thread; database writes run one at a time on a background thread.
 */
object PlayTracker {

    const val MIN_PLAY_MS = 10_000L
    private const val RESUME_WINDOW_MS = 60_000L
    private const val SAVE_EVERY_MS = 5_000L

    enum class Source(val maxGapMs: Long) {
        /** Reels/Shorts/TikTok: the screen updates constantly, so a long silence means we lost sight of it. */
        VIDEO(30_000L),

        /** Music apps report only on changes, so a whole song can pass between two reports. */
        MEDIA(20 * 60_000L),
    }

    private class Record(var playId: Long? = null, var savedMs: Long = 0)

    private class Session(
        val key: String,
        val heard: Heard,
        val startedAtWall: Long,
        var playedMs: Long,
        var lastTick: Long,
        val record: Record,
    )

    private class Ended(val session: Session, val endedAt: Long)

    private val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + writer)
    private var appContext: Context? = null

    private val current = mutableMapOf<Source, Session>()
    private val lastEnded = mutableMapOf<Source, Ended>()

    @Synchronized
    fun observe(context: Context, source: Source, heard: Heard?, alreadyPlayedMs: Long = 0) {
        appContext = context.applicationContext
        if (!Prefs.isListening(context) || heard == null || heard.title.isBlank()) {
            end(source)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val key = SongText.key(heard.title, heard.artist)
        val session = current[source]
        if (session != null && session.key == key) {
            tick(source, session, now)
            persist(session, final = false)
            return
        }
        end(source)
        val previous = lastEnded[source]
        val resumed = previous != null &&
            previous.session.key == key &&
            now - previous.endedAt < RESUME_WINDOW_MS
        current[source] = if (resumed) {
            val old = previous!!.session
            Session(key, old.heard, old.startedAtWall, old.playedMs, now, old.record)
        } else {
            // alreadyPlayedMs: the song was recognised late (by its lyrics), so count the time before that too.
            Session(key, heard, System.currentTimeMillis() - alreadyPlayedMs, alreadyPlayedMs, now, Record())
        }
        current[source]?.let { if (it.playedMs >= MIN_PLAY_MS) persist(it, final = false) }
    }

    /** Stops the current song on one channel (screen off, app closed, music paused). */
    @Synchronized
    fun end(source: Source) {
        val session = current.remove(source) ?: return
        val now = SystemClock.elapsedRealtime()
        tick(source, session, now)
        persist(session, final = true)
        lastEnded[source] = Ended(session, now)
    }

    @Synchronized
    fun endAll() {
        Source.entries.forEach { end(it) }
    }

    private fun tick(source: Source, session: Session, now: Long) {
        val delta = (now - session.lastTick).coerceIn(0, source.maxGapMs)
        session.playedMs += delta
        session.lastTick = now
    }

    private fun persist(session: Session, final: Boolean) {
        val context = appContext ?: return
        val playedMs = session.playedMs
        if (playedMs < MIN_PLAY_MS) return
        val record = session.record
        if (!final && record.playId != null && playedMs - record.savedMs < SAVE_EVERY_MS) return
        val heard = session.heard
        val startedAt = session.startedAtWall
        record.savedMs = playedMs
        scope.launch {
            runCatching {
                val id = record.playId
                if (id == null) {
                    record.playId = Repo.addPlay(
                        context, heard.title, heard.artist, heard.labelRaw,
                        heard.sourceApp, heard.detector, startedAt, playedMs,
                    )
                } else {
                    Repo.updatePlayedMs(context, id, playedMs)
                }
            }
        }
    }
}
