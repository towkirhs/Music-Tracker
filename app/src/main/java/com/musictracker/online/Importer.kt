package com.musictracker.online

import android.content.Context
import android.net.Uri
import com.musictracker.data.Repo
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream

/** Imports listening history from Last.fm and from Spotify / YouTube Music files, in the background. */
object Importer {

    data class State(val running: Boolean = false, val message: String = "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private fun show(running: Boolean, message: String) {
        _state.value = State(running, message)
    }

    /** Your Last.fm scrobbles; a second import only adds the ones newer than the last. */
    fun importLastFm(context: Context) {
        val app = context.applicationContext
        val key = Prefs.lastFmKey(app)
        val user = Prefs.lastFmSessionUser(app).ifBlank { Prefs.lastFmUser(app) }
        if (_state.value.running) return
        if (key.isBlank() || user.isBlank()) return show(false, "Add your Last.fm API key and username first")
        scope.launch {
            show(true, "Reading your Last.fm history…")
            try {
                val from = Prefs.lastFmImportedUntil(app)
                var newest = from
                var added = 0
                var skipped = 0
                var page = 1
                do {
                    val result = LastFmAccount.recentTracks(key, user, page, from)
                    for (s in result.scrobbles) {
                        val ok = Repo.importPlay(app, s.title, s.artist, "Last.fm", "import-lastfm", s.startedAtSec * 1000, HistoryFiles.UNKNOWN_LENGTH_MS)
                        if (ok) added++ else skipped++
                        newest = maxOf(newest, s.startedAtSec)
                    }
                    show(true, "Last.fm: page $page of ${result.totalPages}, $added plays added…")
                    page++
                } while (page <= result.totalPages)
                Prefs.setLastFmImportedUntil(app, newest)
                show(false, "Last.fm: added $added plays" + if (skipped > 0) " ($skipped were already here)" else "")
            } catch (e: Exception) {
                show(false, "Last.fm import stopped: ${e.message ?: "check your internet"}. Run it again to continue.")
            }
        }
    }

    /** Spotify or Google Takeout files: the .zip you downloaded, or the JSON files inside it. */
    fun importFiles(context: Context, uris: List<Uri>) {
        val app = context.applicationContext
        if (_state.value.running || uris.isEmpty()) return
        scope.launch {
            show(true, "Reading files…")
            try {
                val entries = uris.flatMap { readEntries(app, it) }
                if (entries.isEmpty()) {
                    return@launch show(
                        false,
                        "No listening history found. Pick the Spotify zip or its StreamingHistory files, " +
                            "or Takeout's watch-history.json (JSON format).",
                    )
                }
                var added = 0
                entries.sortedBy { it.startedAt }.forEachIndexed { i, e ->
                    if (Repo.importPlay(app, e.title, e.artist, e.source, "import-" + e.source.lowercase().replace(" ", ""), e.startedAt, e.playedMs)) added++
                    if (i % 200 == 0) show(true, "Importing ${i + 1} of ${entries.size}…")
                }
                val sources = entries.map { it.source }.distinct().joinToString(" and ")
                show(false, "$sources: added $added plays" + if (added < entries.size) " (${entries.size - added} were already here)" else "")
            } catch (e: Exception) {
                show(false, "Import failed: ${e.message}")
            }
        }
    }

    private fun readEntries(context: Context, uri: Uri): List<HistoryFiles.Entry> {
        val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
        return input.buffered().use { stream ->
            stream.mark(4)
            val zip = ByteArray(2).also { stream.read(it) }.contentEquals(byteArrayOf(0x50, 0x4B)) // "PK"
            stream.reset()
            if (!zip) {
                HistoryFiles.parse(stream.readBytes().toString(Charsets.UTF_8)).orEmpty()
            } else {
                val all = mutableListOf<HistoryFiles.Entry>()
                ZipInputStream(stream).use { z ->
                    generateSequence { z.nextEntry }.forEach { entry ->
                        if (!entry.isDirectory && HistoryFiles.isHistoryFileName(entry.name)) {
                            HistoryFiles.parse(z.readBytes().toString(Charsets.UTF_8))?.let(all::addAll)
                        }
                    }
                }
                all
            }
        }
    }
}
