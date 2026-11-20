package com.musictracker.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musictracker.data.AppDatabase
import com.musictracker.data.LabelSample
import com.musictracker.data.PlayRow
import com.musictracker.data.Repo
import com.musictracker.data.Unidentified
import com.musictracker.audio.AudioCaptureService
import com.musictracker.lyrics.Genius
import com.musictracker.lyrics.ModelManager
import com.musictracker.lyrics.LyricsLog
import com.musictracker.lyrics.LyricsRecognizer
import com.musictracker.export.Exporter
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.Dispatchers
import com.musictracker.data.Recognition
import com.musictracker.online.Importer
import com.musictracker.online.LastFm
import com.musictracker.online.LastFmAccount
import com.musictracker.online.Scrobbler
import com.musictracker.online.YtMusic
import com.musictracker.online.Lookup
import com.musictracker.player.Radio
import com.musictracker.player.Song
import com.musictracker.player.YouTube
import com.musictracker.update.Updater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportKind(val mime: String, val fileName: String, val done: String) {
    HISTORY_CSV("text/csv", "music-history.csv", "History saved"),
    SONGS_CSV("text/csv", "music-songs.csv", "Song list saved"),
    HTML_REPORT("text/html", "music-report.html", "Report saved"),
    BACKUP("application/json", "music-tracker-backup.json", "Backup saved"),
    LAB_CSV("text/csv", "label-lab.csv", "Label lab text saved"),
}

/** Discovery in the Listen tab, from Last.fm. */
data class DiscoverState(
    val loading: Boolean = false,
    val needsKey: Boolean = false,
    val error: String? = null,
    /** The song the suggestions are based on, and songs like it. */
    val basedOn: LastFm.Track? = null,
    val similar: List<LastFm.Track> = emptyList(),
    val artists: List<String> = emptyList(),
    val bangla: List<LastFm.Track> = emptyList(),
    val hindi: List<LastFm.Track> = emptyList(),
)

/** Your YouTube Music playlists in the Listen tab. */
data class YtLibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val playlists: List<YtMusic.Playlist> = emptyList(),
)

/** A YouTube Music playlist opened from the Listen tab. */
data class PlaylistState(
    val title: String,
    val thumbnail: String?,
    val loading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

/** Stands for "Liked songs" in [MainViewModel.showPlaylist]. */
const val LIKED = "LIKED"

/** Search results in the Listen tab. */
data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).music()

    val plays: StateFlow<List<PlayRow>> = dao.observePlays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val samples: StateFlow<List<LabelSample>> = dao.observeSamples()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unidentified: StateFlow<List<Unidentified>> = dao.observeUnidentified()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listening: StateFlow<Boolean?> = Prefs.listeningFlow(app)
    val lyricsOn: StateFlow<Boolean?> = Prefs.lyricsFlow(app)
    val model: StateFlow<ModelManager.State?> = ModelManager.state(app)
    val audioRunning: StateFlow<Boolean> = AudioCaptureService.running
    val labOn: StateFlow<Boolean?> = Prefs.labFlow(app)

    private val context get() = getApplication<Application>()

    fun setListening(on: Boolean) = Prefs.setListening(context, on)
    fun setLab(on: Boolean) = Prefs.setLab(context, on)

    fun setLyrics(on: Boolean) {
        Prefs.setLyrics(context, on)
        if (on) ModelManager.download(context)?.let { toast(it) }
    }

    fun downloadModel(force: Boolean) {
        ModelManager.download(context, force)?.let { toast(it) }
    }

    fun deleteModel() = ModelManager.delete(context)

    val lyricsLog: StateFlow<List<String>> = LyricsLog.entries

    fun testLyrics() {
        LyricsLog.add("Test: transcribing the last 15 seconds\u2026")
        viewModelScope.launch { LyricsRecognizer.testNow(context) }
    }

    fun wifiOnly(): Boolean = Prefs.isWifiOnly(context)
    fun setWifiOnly(on: Boolean) = Prefs.setWifiOnly(context, on)

    fun geniusToken(): String = Prefs.geniusToken(context)

    fun saveGeniusToken(token: String) {
        Prefs.setGeniusToken(context, token)
        if (token.isBlank()) {
            toast("Genius token removed")
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Genius.test(token.trim()) }
            toast(if (ok) "Genius token works" else "Saved, but Genius didn't accept it (check the token and internet)")
        }
    }

    fun nameUnidentified(item: Unidentified, title: String, artist: String) {
        viewModelScope.launch { Repo.nameUnidentified(context, item, title, artist) }
    }

    fun dismissUnidentified(item: Unidentified) {
        viewModelScope.launch { Repo.dismissUnidentified(context, item.id) }
    }

    fun setFavourite(trackId: Long, favourite: Boolean) {
        viewModelScope.launch { Repo.setFavourite(context, trackId, favourite) }
    }

    fun editTrack(trackId: Long, title: String, artist: String, language: String) {
        viewModelScope.launch { Repo.editTrack(context, trackId, title.trim(), artist.trim(), language) }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch { Repo.deleteTrack(context, trackId) }
    }

    fun deletePlay(row: PlayRow) {
        viewModelScope.launch { Repo.deletePlay(context, row.playId, row.trackId) }
    }

    fun clearSamples() {
        viewModelScope.launch { dao.clearSamples() }
    }

    fun export(kind: ExportKind, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (kind) {
                        ExportKind.HISTORY_CSV -> Exporter.historyCsv(context, uri)
                        ExportKind.SONGS_CSV -> Exporter.songsCsv(context, uri)
                        ExportKind.HTML_REPORT -> Exporter.htmlReport(context, uri)
                        ExportKind.BACKUP -> Exporter.backup(context, uri)
                        ExportKind.LAB_CSV -> Exporter.labCsv(context, uri)
                    }
                }
            }
            toast(if (result.isSuccess) kind.done else "Couldn't save: ${result.exceptionOrNull()?.message}")
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Exporter.restore(context, uri) } }
            toast(
                result.fold(
                    onSuccess = { "Restored $it plays" },
                    onFailure = { "That file isn't a Music Tracker backup" },
                ),
            )
        }
    }

    // Built-in player

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search
    private var searchJob: Job? = null

    fun searchSongs(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return clearSearch()
        searchJob?.cancel()
        _search.value = SearchState(query = q, loading = true)
        searchJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { YouTube.search(q) } }
            _search.value = result.fold(
                onSuccess = { SearchState(query = q, results = it) },
                onFailure = { SearchState(query = q, error = "Search failed: ${it.message ?: "check your internet"}") },
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    fun countPlayer(): Boolean = Prefs.countPlayer(context)
    fun setCountPlayer(on: Boolean) = Prefs.setCountPlayer(context, on)

    // Recognise button

    val recognitions: StateFlow<List<Recognition>> = dao.observeRecognitions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRecognition(item: Recognition) {
        viewModelScope.launch { dao.deleteRecognition(item.id) }
    }

    fun clearRecognitions() {
        viewModelScope.launch { dao.clearRecognitions() }
    }

    private val _shazamOn = MutableStateFlow(Prefs.isShazamOn(app))
    /** Name unlabelled reels by their sound fingerprint. */
    val shazamOn: StateFlow<Boolean> = _shazamOn

    fun setShazamOn(on: Boolean) {
        Prefs.setShazamOn(context, on)
        _shazamOn.value = on
    }

    fun recognizedToLibrary(): Boolean = Prefs.recognizedToLibrary(context)
    fun setRecognizedToLibrary(on: Boolean) = Prefs.setRecognizedToLibrary(context, on)

    // Updates

    val update: StateFlow<Updater.State> = Updater.state

    fun checkForUpdate() {
        viewModelScope.launch { Updater.check(context) }
    }

    fun installUpdate(release: Updater.Release) {
        if (!Updater.canInstall(context)) {
            toast("Allow Music Tracker to install apps, then tap Update again")
            Updater.openInstallPermission(context)
            return
        }
        viewModelScope.launch { Updater.downloadAndInstall(context, release) }
    }

    fun autoUpdateCheck(): Boolean = Prefs.autoUpdateCheck(context)
    fun setAutoUpdateCheck(on: Boolean) = Prefs.setAutoUpdateCheck(context, on)

    // Covers, genre and names from online lookups

    init {
        Lookup.kick(app)
        Scrobbler.kick(app, waitMs = 0) // plays left over from last time, e.g. when offline
    }

    // Last.fm: discovery and radio

    fun lastFmKey(): String = Prefs.lastFmKey(context)
    fun lastFmUser(): String = Prefs.lastFmUser(context)

    fun saveLastFm(key: String, user: String) {
        viewModelScope.launch {
            val k = key.trim()
            val u = user.trim()
            if (k.isEmpty()) {
                Prefs.setLastFm(context, "", u)
                _discover.value = DiscoverState(needsKey = true)
                return@launch toast("Last.fm key removed")
            }
            val keyOk = withContext(Dispatchers.IO) { LastFm.test(k) }
            if (!keyOk) return@launch toast("Last.fm didn't accept that key (check it and the internet)")
            val userOk = u.isEmpty() || withContext(Dispatchers.IO) { LastFm.userExists(k, u) }
            Prefs.setLastFm(context, k, if (userOk) u else "")
            toast(if (userOk) "Last.fm connected" else "Key works, but Last.fm has no user called $u")
            // Songs looked up before the key can now get genre and language from Last.fm tags.
            dao.relookUpWithoutGenre()
            Lookup.kick(context)
            loadDiscover(force = true)
        }
    }

    private val _discover = MutableStateFlow(DiscoverState())
    val discover: StateFlow<DiscoverState> = _discover
    private var discoverJob: Job? = null

    /** Loads suggestions once per app session (or again with [force]). */
    fun loadDiscover(force: Boolean = false) {
        val key = Prefs.lastFmKey(context)
        if (key.isBlank()) {
            _discover.value = DiscoverState(needsKey = true)
            return
        }
        val current = _discover.value
        if (!force && (current.loading || current.basedOn != null || current.bangla.isNotEmpty())) return
        discoverJob?.cancel()
        _discover.value = DiscoverState(loading = true)
        val user = Prefs.lastFmUser(context)
        val month = Stats.songs(plays.value, Range.MONTH).sortedByDescending { it.plays }
        val myArtists = Stats.groups(Stats.inRange(plays.value, Range.MONTH), Stats::artistOf)
            .sortedByDescending { it.plays }.map { it.name }.filter { it != "Unknown artist" }
        discoverJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lastFmTop = if (user.isBlank()) emptyList() else runCatching { LastFm.userTopTracks(key, user, 5) }.getOrDefault(emptyList())
                    // Your most played song this month here, or on Last.fm when this library is new.
                    val seeds = month.take(3).map { LastFm.Track(it.title, it.artist) } + lastFmTop
                    var basedOn: LastFm.Track? = null
                    var similar = emptyList<LastFm.Track>()
                    for (seed in seeds) {
                        similar = LastFm.similarTracks(key, seed.title, seed.artist, 15)
                        if (similar.isNotEmpty()) {
                            basedOn = seed
                            break
                        }
                    }
                    val lastFmArtists = if (user.isBlank()) emptyList() else runCatching { LastFm.userTopArtists(key, user, 5) }.getOrDefault(emptyList())
                    val known = (myArtists + lastFmArtists).map { it.lowercase() }.toSet()
                    val artists = (myArtists.take(3) + lastFmArtists.take(2)).distinct()
                        .flatMap { runCatching { LastFm.similarArtists(key, it, 8) }.getOrDefault(emptyList()) }
                        .distinctBy { it.lowercase() }
                        .filter { it.lowercase() !in known }
                        .take(12)
                    DiscoverState(
                        basedOn = basedOn,
                        similar = similar,
                        artists = artists,
                        bangla = LastFm.tagTopTracks(key, "bangla", 10),
                        hindi = LastFm.tagTopTracks(key, "hindi", 10),
                    )
                }
            }
            _discover.value = result.getOrElse {
                DiscoverState(error = if (it is LastFm.BadKey) it.message else "Couldn't load suggestions. Check your internet.")
            }
        }
    }

    val radioName: StateFlow<String?> = Radio.name

    private fun radio(name: String, playSeedsFirst: Boolean = false, seeds: suspend () -> List<LastFm.Track>) {
        viewModelScope.launch {
            toast("Starting $name\u2026")
            val list = withContext(Dispatchers.IO) { runCatching { seeds() }.getOrDefault(emptyList()) }
            Radio.start(context, name, list, playSeedsFirst)?.let { toast(it) }
        }
    }

    /** Radio of songs like this one. */
    fun startSongRadio(title: String, artist: String) = radio("$title radio") { listOf(LastFm.Track(title, artist)) }

    /** Radio from what you listen to most here (and on Last.fm, if your username is set). */
    fun startMyRadio() {
        val mine = Stats.songs(plays.value, Range.MONTH).sortedByDescending { it.plays }.take(5)
            .map { LastFm.Track(it.title, it.artist) }
        val user = Prefs.lastFmUser(context)
        val key = Prefs.lastFmKey(context)
        radio("Your radio") {
            val fromLastFm = if (user.isBlank() || key.isBlank()) emptyList() else LastFm.userTopTracks(key, user, 5)
            (mine + fromLastFm).shuffled()
        }
    }

    /** Radio from an artist's best-known songs and songs like them. */
    fun startArtistRadio(artist: String) {
        val key = Prefs.lastFmKey(context)
        radio("$artist radio", playSeedsFirst = true) { if (key.isBlank()) emptyList() else LastFm.artistTopTracks(key, artist, 5) }
    }

    /** Radio from a Last.fm tag's chart, e.g. "bangla" or "hindi". */
    fun startTagRadio(label: String, tag: String) {
        val key = Prefs.lastFmKey(context)
        radio("$label radio", playSeedsFirst = true) { if (key.isBlank()) emptyList() else LastFm.tagTopTracks(key, tag, 15) }
    }

    // Accounts: Last.fm scrobbling, imports, YouTube Music library

    val importState: StateFlow<Importer.State> = Importer.state

    fun lastFmSecret(): String = Prefs.lastFmSecret(context)
    fun lastFmSessionUser(): String = Prefs.lastFmSessionUser(context)
    fun scrobbleOn(): Boolean = Prefs.scrobbleOn(context)
    fun setScrobbleOn(on: Boolean) {
        Prefs.setScrobbleOn(context, on)
        if (on) Scrobbler.kick(context, waitMs = 0)
    }

    /** Opens last.fm so you can approve the app; [open] shows the page. Finished by [finishLastFmConnect]. */
    fun connectLastFm(secret: String, open: (String) -> Unit) {
        val key = Prefs.lastFmKey(context)
        if (key.isBlank()) return toast("Save your Last.fm API key first")
        if (secret.isBlank()) return toast("Paste the shared secret from the same Last.fm page")
        Prefs.setLastFmSecret(context, secret)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LastFmAccount.startConnect(key, secret.trim()) } }
            result.onSuccess { (token, url) ->
                Prefs.setLastFmPendingToken(context, token)
                open(url)
            }.onFailure { toast("Last.fm didn't accept the key and secret: ${it.message}") }
        }
    }

    private val _lastFmUser = MutableStateFlow(Prefs.lastFmSessionUser(app))
    /** The Last.fm account scrobbles go to, or "" when not connected. */
    val lastFmConnected: StateFlow<String> = _lastFmUser

    /** Called when you come back from last.fm: completes connecting if you approved. */
    fun finishLastFmConnect() {
        val token = Prefs.lastFmPendingToken(context).ifBlank { return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LastFmAccount.finishConnect(Prefs.lastFmKey(context), Prefs.lastFmSecret(context), token) }
            }
            result.onSuccess { (session, user) ->
                Prefs.setLastFmPendingToken(context, "")
                Prefs.setLastFmSession(context, session, user)
                // Only plays from now on are sent; older ones would be refused by Last.fm anyway.
                Prefs.setScrobbledUntil(context, System.currentTimeMillis())
                if (Prefs.lastFmUser(context).isBlank()) Prefs.setLastFm(context, Prefs.lastFmKey(context), user)
                _lastFmUser.value = user
                toast("Scrobbling to Last.fm as $user")
            }.onFailure {
                // 14: not approved yet. Keep the token so coming back again can finish.
                if ((it as? LastFmAccount.Rejected)?.code != 14) {
                    Prefs.setLastFmPendingToken(context, "")
                    toast("Couldn't connect Last.fm: ${it.message}")
                }
            }
        }
    }

    fun disconnectLastFm() {
        Prefs.setLastFmSession(context, "", "")
        _lastFmUser.value = ""
        toast("Last.fm scrobbling turned off")
    }

    fun importLastFm() = Importer.importLastFm(context)
    fun importFiles(uris: List<Uri>) = Importer.importFiles(context, uris)

    private val _ytCookie = MutableStateFlow(Prefs.ytMusicCookie(app))
    val ytSignedIn: StateFlow<Boolean> = _ytCookie.map { YtMusic.isSignedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, YtMusic.isSignedIn(_ytCookie.value))

    fun saveYtCookie(cookie: String) {
        if (!YtMusic.isSignedIn(cookie)) return toast("Sign-in didn't finish. Try again.")
        Prefs.setYtMusicCookie(context, cookie)
        _ytCookie.value = cookie
        toast("Signed in to YouTube Music")
        loadYtLibrary(force = true)
    }

    fun signOutYt() {
        Prefs.setYtMusicCookie(context, "")
        _ytCookie.value = ""
        _ytLibrary.value = YtLibraryState()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        toast("Signed out of YouTube Music")
    }

    private val _ytLibrary = MutableStateFlow(YtLibraryState())
    val ytLibrary: StateFlow<YtLibraryState> = _ytLibrary

    /** Your playlists, loaded once per app session (or again with [force]). */
    fun loadYtLibrary(force: Boolean = false) {
        val cookie = _ytCookie.value
        if (!YtMusic.isSignedIn(cookie)) return
        val current = _ytLibrary.value
        if (!force && (current.loading || current.loaded)) return
        _ytLibrary.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { YtMusic.playlists(cookie) } }
            _ytLibrary.value = result.fold(
                onSuccess = { YtLibraryState(loaded = true, playlists = it) },
                onFailure = { YtLibraryState(error = it.message ?: "Couldn't load your YouTube Music library") },
            )
        }
    }

    private val _openPlaylist = MutableStateFlow<PlaylistState?>(null)
    /** The YouTube Music playlist being shown, or null. */
    val openPlaylist: StateFlow<PlaylistState?> = _openPlaylist
    private var playlistJob: Job? = null

    fun showPlaylist(browseId: String, title: String, thumbnail: String?) {
        val cookie = _ytCookie.value
        playlistJob?.cancel()
        _openPlaylist.value = PlaylistState(title, thumbnail, loading = true)
        playlistJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { if (browseId == LIKED) YtMusic.likedSongs(cookie) else YtMusic.playlistSongs(cookie, browseId) }
            }
            _openPlaylist.value = PlaylistState(
                title, thumbnail,
                songs = result.getOrDefault(emptyList()),
                error = result.exceptionOrNull()?.let { it.message ?: "Couldn't load the songs" },
            )
        }
    }

    fun closePlaylist() {
        playlistJob?.cancel()
        _openPlaylist.value = null
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
