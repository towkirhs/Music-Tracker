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
    val uriHandler = LocalUriHandler.current

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
            Group("Recognise songs by lyrics") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Name covers and \"Original audio\" reels")
                        Text(
                            "When a reel has no song name, the app listens to 15 seconds, turns the singing into " +
                                "text on your phone (Whisper), and looks for the song. Free and offline; Genius is optional.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = lyricsOn == true, onCheckedChange = { vm.setLyrics(it) })
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
            Text(title, style = MaterialTheme.typography.titleMedium)
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
