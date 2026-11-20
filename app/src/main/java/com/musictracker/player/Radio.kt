package com.musictracker.player

import android.content.Context
import com.musictracker.data.SongText
import com.musictracker.online.LastFm
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Endless radio from Last.fm's "similar songs": starts from a few seed songs, plays them through the
 * built-in player, and adds more similar songs whenever only a few are left in the queue.
 * It stops by itself when you play something else.
 */
object Radio {

    private val scope: CoroutineScope = MainScope()
    private var job: Job? = null

    private val _name = MutableStateFlow<String?>(null)
    /** What the radio is playing from, e.g. "Tum Hi Ho radio", or null when off. */
    val name: StateFlow<String?> = _name

    /** Songs already queued, so the radio doesn't repeat itself. */
    private val queued = mutableSetOf<String>()

    private fun keyOf(t: LastFm.Track) = SongText.key(t.title, t.artist)

    /**
     * Starts a radio called [name] from [seeds] (songs to start near). With [playSeedsFirst] the seeds
     * themselves play first, as in a chart radio. Returns an error message, or null when it started.
     */
    suspend fun start(
        context: Context,
        name: String,
        seeds: List<LastFm.Track>,
        playSeedsFirst: Boolean = false,
    ): String? {
        val key = Prefs.lastFmKey(context)
        if (key.isBlank()) return "Add your free Last.fm API key in Settings to use radio"
        if (seeds.isEmpty()) return "Nothing to start the radio from yet"
        job?.cancel()
        queued.clear()
        val first = withContext(Dispatchers.IO) {
            runCatching {
                val similar = similarTo(key, seeds.take(5))
                if (playSeedsFirst) seeds.shuffled() + similar else similar
            }
        }.getOrElse { return if (it is LastFm.BadKey) it.message else "Radio needs the internet" }
            .filter { queued.add(keyOf(it)) }
            .take(30)
        if (first.isEmpty()) return "Last.fm has no similar songs for this one yet"
        val songs = first.map { Song(videoId = null, title = it.title, artist = it.artist) }
        MusicPlayer.play(context, songs)
        _name.value = name
        job = scope.launch { keepFilling(context, key, songs.first()) }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        _name.value = null
    }

    private suspend fun keepFilling(context: Context, key: String, firstSong: Song) {
        var filling = false
        MusicPlayer.state.collect { state ->
            // Something else is playing now: the radio is over.
            if (state.queue.isNotEmpty() && state.queue.first().query != firstSong.query) return@collect stop()
            if (filling || state.queue.isEmpty() || state.index < state.queue.size - 3) return@collect
            filling = true
            val recent = state.queue.takeLast(3).map { LastFm.Track(it.title, it.artist) }
            val more = withContext(Dispatchers.IO) { runCatching { similarTo(key, recent) }.getOrDefault(emptyList()) }
                .filter { queued.add(keyOf(it)) }
                .take(15)
            more.forEach { MusicPlayer.addToQueue(context, Song(videoId = null, title = it.title, artist = it.artist)) }
            // Wait for the queue to grow before checking again; if nothing was found, stay quiet.
            filling = more.isEmpty()
        }
    }

    /**
     * Songs similar to [seeds], mixed so no seed dominates. When Last.fm knows no similar songs
     * (common for new Bangla songs), it falls back to similar artists' hits, then the artist's own.
     */
    private fun similarTo(key: String, seeds: List<LastFm.Track>): List<LastFm.Track> {
        val lists = seeds.map { seed ->
            LastFm.similarTracks(key, seed.title, seed.artist, 20).ifEmpty {
                if (seed.artist.isBlank()) return@ifEmpty emptyList()
                LastFm.similarArtists(key, seed.artist, 4)
                    .flatMap { LastFm.artistTopTracks(key, it, 4) }
                    .ifEmpty { LastFm.artistTopTracks(key, seed.artist, 10) }
            }.shuffled()
        }
        // Take one from each list in turn.
        val mixed = mutableListOf<LastFm.Track>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(mixed::add) }
        return mixed.distinctBy(::keyOf)
    }
}
