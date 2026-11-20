@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musictracker.BuildConfig
import com.musictracker.lyrics.ModelManager
import com.musictracker.update.Updater

@Composable
fun SettingsScreen(vm: MainViewModel, onRequestAudio: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val readerOn = remember(refresh) { Setup.labelReaderOn(context) }
    val nowPlayingOn = remember(refresh) { Setup.nowPlayingOn(context) }
    val batteryOk = remember(refresh) { Setup.batteryUnrestricted(context) }
    val labOn by vm.labOn.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    var showSamples by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val lyricsOn by vm.lyricsOn.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val audioRunning by vm.audioRunning.collectAsStateWithLifecycle()
    var wifiOnly by remember { mutableStateOf(vm.wifiOnly()) }
    var token by remember { mutableStateOf(vm.geniusToken()) }
    var lastFmKey by remember { mutableStateOf(vm.lastFmKey()) }
    var lastFmUser by remember { mutableStateOf(vm.lastFmUser()) }
    val uriHandler = LocalUriHandler.current
    val lyricsLog by vm.lyricsLog.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val shazamOn by vm.shazamOn.collectAsStateWithLifecycle()
    var autoUpdate by remember { mutableStateOf(vm.autoUpdateCheck()) }
    var countPlayer by remember { mutableStateOf(vm.countPlayer()) }
    var recognizedToLibrary by remember { mutableStateOf(vm.recognizedToLibrary()) }

    val launchers = ExportKind.entries.associateWith { kind ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(kind.mime)) { uri ->
            if (uri != null) vm.export(kind, uri)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Group("Setup") {
                SetupRow(
                    done = readerOn,
                    title = "Song reader",
                    detail = "Reads the song name on Reels, Shorts and TikTok. In the list, open Music Tracker song reader and turn it on.",
                    onFix = { Setup.openLabelReader(context) },
                )
                SetupRow(
                    done = nowPlayingOn,
                    title = "Music app reader",
                    detail = "Reads what Spotify, YouTube Music and other players are playing. Turn on Music Tracker now playing.",
                    onFix = { Setup.openNowPlaying(context) },
                )
                SetupRow(
                    done = batteryOk,
                    title = "No battery limits",
                    detail = "Stops Android from pausing the app in the background.",
                    onFix = { Setup.openBattery(context) },
                )
                Text(
                    "Switch greyed out? Android blocks these for apps installed from a file. Open App info, tap ⋮ (top right), " +
                        "choose Allow restricted settings, then come back and try again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { Setup.openAppInfo(context) }) { Text("Open App info") }
                Text(
                    "Nothing Phone (2a): in App info → Battery choose Unrestricted. Also add the Music Tracker tile to " +
                        "Quick Settings to start or stop without opening the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Group("Updates") {
                Text("Music Tracker ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                when (val u = update) {
                    Updater.State.Idle -> {}
                    Updater.State.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is Updater.State.UpToDate -> Text("\u2713 You have the newest version.")
                    is Updater.State.Available -> {
                        Text("New version: ${u.release.name}", style = MaterialTheme.typography.bodyLarge)
                        if (u.release.notes.isNotBlank()) {
                            Text(u.release.notes.take(400), style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = { vm.installUpdate(u.release) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Update now (${u.release.sizeBytes / 1_000_000} MB)")
                        }
                    }
                    is Updater.State.Downloading -> Column {
                        Text("Downloading update: ${u.percent}%")
                        LinearProgressIndicator(
                            progress = { u.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                    Updater.State.Installing -> Text("Confirm the install on the screen Android shows. Your library is kept.")
                    is Updater.State.Failed -> Text(u.reason, style = MaterialTheme.typography.bodySmall)
                }
                if (update !is Updater.State.Available && update !is Updater.State.Downloading) {
                    FilledTonalButton(onClick = { vm.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Check for updates")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check once a day when the app opens", modifier = Modifier.weight(1f))
                    Switch(checked = autoUpdate, onCheckedChange = { autoUpdate = it; vm.setAutoUpdateCheck(it) })
                }
                Text(
                    "Updates come from this app's GitHub releases and install over this version, keeping your library. " +
                        "The first time, Android asks you to allow Music Tracker to install apps.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Group("Player and Recognise") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count songs played here")
                        Text(
                            "Songs you play in the Listen tab are added to your library and stats, even when Listening is off.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = countPlayer, onCheckedChange = { countPlayer = it; vm.setCountPlayer(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add recognised songs to the library")
                        Text(
                            "Songs named with the Recognise button are logged as a play.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = recognizedToLibrary,
                        onCheckedChange = { recognizedToLibrary = it; vm.setRecognizedToLibrary(it) },
                    )
                }
                Text(
                    "Songs play from YouTube Music, with no account and no ads. Played songs are cached (up to 512 MB) " +
                        "so replays use no data. Lyrics come from LRCLIB.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Group("Recognise songs with no name") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Name covers and \"Original audio\" reels")
                        Text(
                            "When a reel has no song name, the app listens to it and looks the song up: first by its " +
                                "sound (Shazam), then by turning the singing into text on your phone (Whisper).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = lyricsOn == true, onCheckedChange = { vm.setLyrics(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recognise by sound (Shazam)")
                        Text(
                            "Fast and works for songs without words. Sends a short sound fingerprint, never the audio, " +
                                "over the internet. Works without the speech model.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = shazamOn, onCheckedChange = { vm.setShazamOn(it) })
                }
                when (val m = model) {
                    is ModelManager.State.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\u2713 Speech model ready (57 MB)", modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteModel() }) { Text("Delete") }
                    }
                    is ModelManager.State.Downloading -> Column {
                        Text("Downloading speech model: ${m.percent}%")
                        LinearProgressIndicator(
                            progress = { m.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                    is ModelManager.State.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.reason, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { vm.downloadModel(force = false) }) { Text("Try again") }
                            TextButton(onClick = { vm.downloadModel(force = true) }) { Text("Use mobile data") }
                        }
                    }
                    else -> FilledTonalButton(onClick = { vm.downloadModel(force = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Download speech model (57 MB, once)")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Download only on Wi-Fi", modifier = Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; vm.setWifiOnly(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (audioRunning) "\u2713 Audio: listening" else "\u2715 Audio: not allowed right now",
                        modifier = Modifier.weight(1f),
                    )
                    if (!audioRunning) FilledTonalButton(onClick = onRequestAudio) { Text("Allow audio") }
                }
                OutlinedButton(onClick = { vm.testLyrics() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Test now: recognise the last 15 seconds")
                }
                Text("Recent activity", style = MaterialTheme.typography.labelLarge)
                if (lyricsLog.isEmpty()) {
                    Text(
                        "Nothing yet. Play a reel with no song name for 12 seconds, or tap Test now while a song plays.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                lyricsLog.take(12).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(
                    "Optional, free: a Genius account lets the app search lyrics online. Sign up at genius.com/api-clients, " +
                        "create an API client (any name and website), then copy its Client Access Token here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uriHandler.openUri("https://genius.com/api-clients") }) { Text("Open genius.com/api-clients") }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Genius Client Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { vm.saveGeniusToken(token) }) { Text("Save and test token") }
            }
        }
        item {
            Group("Last.fm: radio and discovery") {
                Text(
                    "Free. Radio, \"Because you listened to\" and artist suggestions come from Last.fm, and its tags " +
                        "help tell a song's genre and whether it is Bangla, Hindi or English. Create an API account at " +
                        "last.fm/api/account/create (any app name; leave callback empty), then paste the API key here. " +
                        "Add your Last.fm username to also use what you listen to on Last.fm.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uriHandler.openUri("https://www.last.fm/api/account/create") }) {
                    Text("Open last.fm/api/account/create")
                }
                OutlinedTextField(
                    value = lastFmKey,
                    onValueChange = { lastFmKey = it },
                    label = { Text("Last.fm API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastFmUser,
                    onValueChange = { lastFmUser = it },
                    label = { Text("Last.fm username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { vm.saveLastFm(lastFmKey, lastFmUser) }) { Text("Save and test") }
            }
        }
        item {
            Group("Export and backup") {
                Text(
                    "Files are saved on your phone only. CSV opens in Excel or Google Sheets. For a PDF, open the report " +
                        "in Chrome and use Share → Print → Save as PDF.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ExportButton("Song list (CSV, for Excel)") { launchers.getValue(ExportKind.SONGS_CSV).launch(ExportKind.SONGS_CSV.fileName) }
                ExportButton("Full history (CSV, for Excel)") { launchers.getValue(ExportKind.HISTORY_CSV).launch(ExportKind.HISTORY_CSV.fileName) }
                ExportButton("Report (HTML, print to PDF)") { launchers.getValue(ExportKind.HTML_REPORT).launch(ExportKind.HTML_REPORT.fileName) }
                ExportButton("Backup file") { launchers.getValue(ExportKind.BACKUP).launch(ExportKind.BACKUP.fileName) }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore from backup file")
                }
            }
        }
        item {
            Group("Label lab") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Save screen text from Reels, Shorts and TikTok")
                        Text(
                            "Turn on for a few minutes if songs are missed. It records what the song reader sees, so the " +
                                "reading rules can be fixed for your phone. Only those apps, only on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = labOn == true, onCheckedChange = { vm.setLab(it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSamples = !showSamples }) {
                        Text(if (showSamples) "Hide (${samples.size})" else "Show (${samples.size})")
                    }
                    OutlinedButton(onClick = { launchers.getValue(ExportKind.LAB_CSV).launch(ExportKind.LAB_CSV.fileName) }) {
                        Text("Save CSV")
                    }
                    TextButton(onClick = { vm.clearSamples() }) { Text("Clear") }
                }
            }
        }
        if (showSamples) {
            items(samples.take(150), key = { "s-" + it.id }) { s ->
                Text(
                    "${s.sourceApp} · ${s.viewId.ifBlank { "-" }}\n${s.content}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        item {
            Text(
                "Music Tracker ${BuildConfig.VERSION_NAME} · free, offline, no account. Songs from Reels, Shorts and " +
                    "TikTok count after 10 seconds.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces your current library with the one in the backup file.") },
            confirmButton = { TextButton(onClick = { vm.restore(uri); restoreUri = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SetupRow(done: Boolean, title: String, detail: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (done) "✓ " else "✕ ") + title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (!done) FilledTonalButton(onClick = onFix) { Text("Turn on") }
    }
}

@Composable
private fun ExportButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
