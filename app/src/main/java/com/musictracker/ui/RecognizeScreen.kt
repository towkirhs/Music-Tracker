@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musictracker.data.Recognition
import com.musictracker.data.SongText
import com.musictracker.player.MusicPlayer
import com.musictracker.player.Song
import com.musictracker.recognize.SongRecognizer
import com.musictracker.tracking.Prefs

private fun Recognition.toSong() = Song(youtubeVideoId.ifBlank { null }, title, artist, coverUrl.ifBlank { null })

/** Shazam-style recognition: one big button, the answer, and everything named before. */
@Composable
fun RecognizeScreen(vm: MainViewModel, onDismiss: () -> Unit, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    val state by SongRecognizer.state.collectAsStateWithLifecycle()
    val history by vm.recognitions.collectAsStateWithLifecycle()
    val audioRunning by vm.audioRunning.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(SongRecognizer.Input.AUTO) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) SongRecognizer.start(context, SongRecognizer.Input.MIC) else vm.toast("Microphone not allowed")
    }
    fun start() {
        val needsMic = input == SongRecognizer.Input.MIC ||
            (input == SongRecognizer.Input.AUTO && !SongRecognizer.phoneAudioAvailable())
        if (needsMic && !SongRecognizer.hasMicPermission(context)) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            SongRecognizer.start(context, input)
        }
    }
    DisposableEffect(Unit) { onDispose { SongRecognizer.reset() } }

    fun playNow(r: Recognition) {
        MusicPlayer.play(context, listOf(r.toSong()))
        onDismiss()
        onOpenPlayer()
    }

    Dialog(onDismissRequest = { SongRecognizer.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recognise", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { SongRecognizer.cancel(); onDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SongRecognizer.Input.entries.forEach { i ->
                            FilterChip(
                                selected = input == i,
                                enabled = i != SongRecognizer.Input.PHONE || audioRunning,
                                onClick = { input = i },
                                label = { Text(i.label) },
                            )
                        }
                    }
                }
                item {
                    val listening = state as? SongRecognizer.State.Listening
                    ListenButton(listening?.level, onClick = { if (listening != null) SongRecognizer.cancel() else start() })
                }
                item {
                    Text(
                        when (val s = state) {
                            SongRecognizer.State.Idle -> "Tap to recognise"
                            is SongRecognizer.State.Listening -> "Listening via ${s.heardFrom.lowercase()}… ${(s.progress * 12).toInt()} s"
                            is SongRecognizer.State.Found -> "Found it"
                            is SongRecognizer.State.NotFound -> s.message
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (input != SongRecognizer.Input.MIC && !audioRunning) {
                    item {
                        Text(
                            "To recognise what plays on this phone (Reels, videos), allow audio on Home first. " +
                                "Until then the microphone is used.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                (state as? SongRecognizer.State.Found)?.let { found ->
                    item { ResultCard(found.recognition, onPlay = { playNow(found.recognition) }) }
                }
                if (history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionTitle("Recognised before") }
                            TextButton(onClick = { vm.clearRecognitions() }) { Text("Clear") }
                        }
                    }
                    items(history, key = { it.id }) { r ->
                        ListItem(
                            modifier = Modifier.clickable { playNow(r) },
                            leadingContent = { Artwork(r.coverUrl, 48.dp) },
                            headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text("${r.artist} · ${Stats.date(r.recognizedAt)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.deleteRecognition(r) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The round button; it pulses while listening and swells with the sound level. */
@Composable
private fun ListenButton(level: Float?, onClick: () -> Unit) {
    val listening = level != null
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val loud by animateFloatAsState(1f + (level ?: 0f) * 0.25f, label = "level")
    Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(200.dp)
                    .scale(pulse * loud)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            )
        }
        Box(
            Modifier
                .size(170.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = if (listening) "Stop listening" else "Recognise",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun ResultCard(r: Recognition, onPlay: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(r.coverUrl, 200.dp)
            Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(r.artist, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (r.album.isNotBlank()) Text(r.album, style = MaterialTheme.typography.bodySmall)
            if (Prefs.recognizedToLibrary(context)) Text("Saved to your library.", style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("▶ Play here") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    open(
                        r.youtubeVideoId.ifBlank { null }?.let { "https://music.youtube.com/watch?v=$it" }
                            ?: SongText.youtubeUrl(r.title, r.artist),
                    )
                }) { Text("YouTube") }
                if (r.spotifyUrl.isNotBlank()) OutlinedButton(onClick = { open(r.spotifyUrl) }) { Text("Spotify") }
                if (r.shazamUrl.isNotBlank()) OutlinedButton(onClick = { open(r.shazamUrl) }) { Text("Shazam") }
            }
        }
    }
}
