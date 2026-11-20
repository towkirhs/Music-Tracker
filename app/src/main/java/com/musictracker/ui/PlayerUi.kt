@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.musictracker.player.Lyrics
import com.musictracker.player.MusicPlayer
import com.musictracker.player.Song
import com.musictracker.player.SyncedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Album art, or a note on a tinted square when there is none. */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (size > 100.dp) 16.dp else 8.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size / 2),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Position in the current song, refreshed twice a second while shown. */
@Composable
private fun rememberPosition(): Long {
    var position by remember { mutableLongStateOf(MusicPlayer.position()) }
    LaunchedEffect(Unit) {
        while (true) {
            position = MusicPlayer.position()
            delay(500)
        }
    }
    return position
}

/** The bar above the tabs while something is playing. Tap it for the full player. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.current ?: return
    val position = rememberPosition()
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnail, 44.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        song.artist.ifBlank { "Unknown artist" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PlayPauseButton(state.isPlaying, state.isLoading, filled = false)
                IconButton(onClick = { MusicPlayer.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, loading: Boolean, filled: Boolean) {
    val content: @Composable () -> Unit = {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            playing -> Icon(Icons.Default.Pause, contentDescription = "Pause")
            else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
    if (filled) {
        FilledIconButton(onClick = { MusicPlayer.togglePlay() }, modifier = Modifier.size(72.dp), content = content)
    } else {
        IconButton(onClick = { MusicPlayer.togglePlay() }, content = content)
    }
}

private enum class PlayerPane(val label: String) { LYRICS("Lyrics"), QUEUE("Up next") }

/** The full player: art, seek bar, controls, synced lyrics, queue, speed and sleep timer. */
@Composable
fun PlayerScreen(onDismiss: () -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.current
    if (song == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var pane by remember { mutableStateOf<PlayerPane?>(null) }
    var showSound by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val sleepAt by MusicPlayer.sleepTimer.collectAsStateWithLifecycle()
    val position = rememberPosition()
    val lyrics by produceState<Lyrics?>(null, song.title, song.artist) {
        value = null
        value = withContext(Dispatchers.IO) { SyncedLyrics.find(song.title, song.artist, state.durationMs) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player") }
                    Text("Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (sleepAt > 0) {
                        Text(
                            "Sleep in ${((sleepAt - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (pane) {
                        null -> Artwork(song.thumbnail, 300.dp, Modifier.aspectRatio(1f))
                        PlayerPane.LYRICS -> LyricsPane(lyrics, position)
                        PlayerPane.QUEUE -> QueuePane(state.queue, state.index)
                    }
                }

                Column {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(song.artist.ifBlank { "Unknown artist" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                SeekBar(position, state.durationMs)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { MusicPlayer.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { MusicPlayer.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }
                    PlayPauseButton(state.isPlaying, state.isLoading, filled = true)
                    IconButton(onClick = { MusicPlayer.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { MusicPlayer.cycleRepeat() }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerPane.entries.forEach { p ->
                        FilterChip(selected = pane == p, onClick = { pane = if (pane == p) null else p }, label = { Text(p.label) })
                    }
                    FilterChip(
                        selected = state.speed != 1f || state.pitch != 1f,
                        onClick = { showSound = true },
                        label = { Text("Speed ${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}×") },
                    )
                    FilterChip(selected = sleepAt > 0, onClick = { showSleep = true }, label = { Text("Sleep") })
                }
            }
        }
    }

    if (showSound) SoundDialog(state.speed, state.pitch) { showSound = false }
    if (showSleep) SleepDialog(sleepAt > 0) { showSleep = false }
}

@Composable
private fun SeekBar(position: Long, duration: Long) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { MusicPlayer.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
        )
        Row {
            Text(clock((fraction * duration).toLong()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(clock(duration), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Lyrics that follow the song; the current line is highlighted and kept in view. */
@Composable
private fun LyricsPane(lyrics: Lyrics?, position: Long) {
    when {
        lyrics == null -> CircularProgressIndicator()
        lyrics.lines.isEmpty() -> Text(
            "No lyrics found for this song.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        else -> {
            val current = if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= position + 300 } else -1
            val listState = rememberLazyListState()
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lyrics.lines) { i, line ->
                    val active = i == current
                    Text(
                        line.text.ifBlank { "♪" },
                        style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active || !lyrics.synced) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.timeMs >= 0) { MusicPlayer.seekTo(line.timeMs) },
                    )
                }
                if (!lyrics.synced) {
                    item { Text("Lyrics from LRCLIB (not timed)", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun QueuePane(queue: List<Song>, index: Int) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(queue) { i, s ->
            ListItem(
                modifier = Modifier.clickable { MusicPlayer.skipTo(i) },
                leadingContent = { Artwork(s.thumbnail, 40.dp) },
                headlineContent = {
                    Text(
                        (if (i == index) "▶ " else "") + s.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                supportingContent = { Text(s.artist.ifBlank { "Unknown artist" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = if (i != index) {
                    { IconButton(onClick = { MusicPlayer.removeAt(i) }) { Icon(Icons.Default.Close, contentDescription = "Remove") } }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SoundDialog(speed: Float, pitch: Float, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var s by remember { mutableFloatStateOf(speed) }
    var p by remember { mutableFloatStateOf(pitch) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed and pitch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speed ${"%.2f".format(s)}×")
                Slider(value = s, onValueChange = { s = it }, valueRange = 0.5f..2f, steps = 29)
                Text("Pitch ${"%.2f".format(p)}×")
                Slider(value = p, onValueChange = { p = it }, valueRange = 0.5f..2f, steps = 29)
                Text("Kept for the next songs too.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { MusicPlayer.setSpeedPitch(context, s, p); onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { MusicPlayer.setSpeedPitch(context, 1f, 1f); onDismiss() }) { Text("Reset") }
        },
    )
}

@Composable
private fun SleepDialog(on: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(10, 15, 30, 45, 60, 90).forEach { m ->
                    TextButton(onClick = { MusicPlayer.setSleepTimer(m); onDismiss() }) { Text("Pause in $m minutes") }
                }
            }
        },
        confirmButton = {
            if (on) TextButton(onClick = { MusicPlayer.setSleepTimer(0); onDismiss() }) { Text("Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
