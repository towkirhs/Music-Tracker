@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.musictracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.atan2

/**
 * Album art. With no cover, a gradient tile picked from [seed] (usually the title), so each song
 * keeps its own colour.
 */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, seed: String = "", shape: Shape? = null) {
    val (from, to) = ArtPalette[Math.floorMod(seed.hashCode(), ArtPalette.size)]
    Box(
        modifier
            .size(size)
            .clip(shape ?: if (size > 100.dp) MaterialTheme.shapes.large else MaterialTheme.shapes.small)
            .background(Brush.linearGradient(listOf(from, to))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(size / 2.5f),
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.outline,
                drawStopIndicator = {},
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnail, 44.dp, seed = song.title)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        song.artist.ifBlank { "Unknown artist" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlayPauseButton(state.isPlaying, state.isLoading, filled = false)
                IconButton(onClick = { MusicPlayer.next() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
            }
        }
    }
}


/** The play/pause button: a big white circle in the player, a plain orange icon in the mini player. */
@Composable
private fun PlayPauseButton(playing: Boolean, loading: Boolean, filled: Boolean) {
    if (filled) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable { MusicPlayer.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            val ink = MaterialTheme.colorScheme.surface
            when {
                loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = ink)
                playing -> Icon(Icons.Default.Pause, contentDescription = "Pause", tint = ink, modifier = Modifier.size(36.dp))
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = ink, modifier = Modifier.size(36.dp))
            }
        }
    } else {
        IconButton(
            onClick = { MusicPlayer.togglePlay() },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                playing -> Icon(Icons.Default.Pause, contentDescription = "Pause")
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

/** An icon button that turns orange while its setting is on. */
@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class PlayerPane(val label: String) { QUEUE("Next Songs"), LYRICS("Lyrics") }

/**
 * The full player: title and heart, round cover with a progress ring you can drag to seek, controls,
 * and a Next Songs / Lyrics panel that expands to full height. Speed, sleep timer and radio are in the ⋮ menu.
 */
@Composable
fun PlayerScreen(vm: MainViewModel, onDismiss: () -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.current
    if (song == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var pane by remember { mutableStateOf(PlayerPane.QUEUE) }
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSound by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val sleepAt by MusicPlayer.sleepTimer.collectAsStateWithLifecycle()
    val position = rememberPosition()
    val lyrics by produceState<Lyrics?>(null, song.title, song.artist) {
        value = null
        value = withContext(Dispatchers.IO) { SyncedLyrics.find(song.title, song.artist, state.durationMs) }
    }
    val trackFlow = remember(song.title, song.artist) { vm.libraryTrack(song.title, song.artist) }
    val track by trackFlow.collectAsStateWithLifecycle(null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .background(Brush.verticalGradient(PlayerGradient))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                // Top bar: close, handle, menu.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                        if (sleepAt > 0) {
                            Text(
                                "Sleep in ${((sleepAt - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Speed and pitch") },
                                onClick = { showMenu = false; showSound = true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (sleepAt > 0) "Sleep timer (on)" else "Sleep timer") },
                                onClick = { showMenu = false; showSleep = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Start radio from this song") },
                                onClick = { showMenu = false; vm.startSongRadio(song.title, song.artist) },
                            )
                        }
                    }
                }

                // Title, artist and heart (with a small cover when the panel is expanded).
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (expanded) Artwork(song.thumbnail, 56.dp, Modifier.padding(end = 16.dp), seed = song.title)
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = if (expanded) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                            maxLines = if (expanded) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.artist.ifBlank { "Unknown artist" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    track?.let { t ->
                        IconButton(onClick = { vm.setFavourite(t.id, !t.isFavourite) }) {
                            Icon(
                                if (t.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (t.isFavourite) "Unheart" else "Heart",
                                tint = if (t.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                if (expanded) {
                    Box(Modifier.padding(top = 12.dp)) { SeekBar(position, state.durationMs) }
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { ProgressDisc(song, position, state.durationMs) }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToggleIcon(Icons.Default.Shuffle, "Shuffle", state.shuffle) { MusicPlayer.toggleShuffle() }
                    IconButton(onClick = { MusicPlayer.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
                    }
                    PlayPauseButton(state.isPlaying, state.isLoading, filled = true)
                    IconButton(onClick = { MusicPlayer.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
                    }
                    ToggleIcon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        state.repeatMode != Player.REPEAT_MODE_OFF,
                    ) { MusicPlayer.cycleRepeat() }
                }

                // Next Songs / Lyrics panel.
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerPane.entries.forEach { p ->
                        Text(
                            p.label,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (p == pane) FontWeight.Bold else FontWeight.Medium),
                            color = if (p == pane) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = 20.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { pane = p },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (expanded) "Show cover" else "Expand",
                        )
                    }
                }
                Box(
                    (if (expanded) Modifier.weight(1f) else Modifier.height(168.dp))
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    when (pane) {
                        PlayerPane.QUEUE -> QueuePane(state.queue, state.index, vm)
                        PlayerPane.LYRICS -> LyricsPane(lyrics, position)
                    }
                }
            }
        }
    }

    if (showSound) SoundDialog(state.speed, state.pitch) { showSound = false }
    if (showSleep) SleepDialog(sleepAt > 0) { showSleep = false }
}

/**
 * The round cover inside a dark disc, with the song's progress drawn as a ring from the bottom,
 * clockwise. Drag around the ring to seek.
 */
@Composable
private fun ProgressDisc(song: Song, position: Long, duration: Long) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val disc = MaterialTheme.colorScheme.surfaceContainerHigh
    val track = MaterialTheme.colorScheme.outline
    val progress = MaterialTheme.colorScheme.primary
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight, 320.dp)
        Box(
            Modifier
                .size(size)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragging = ringFraction(it, this.size) },
                        onDrag = { change, _ -> dragging = ringFraction(change.position, this.size) },
                        onDragEnd = {
                            dragging?.let { MusicPlayer.seekTo((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    )
                }
                .drawBehind {
                    drawCircle(disc)
                    val stroke = 4.dp.toPx()
                    val inset = 12.dp.toPx()
                    val ring = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
                    drawArc(track, 90f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = ring, style = Stroke(stroke))
                    drawArc(
                        progress, 90f, 360f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Artwork(song.thumbnail, size * 0.72f, seed = song.title, shape = CircleShape)
            Text(
                clock((fraction * duration).toLong()) + if (duration > 0) " / " + clock(duration) else "",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(CircleShape)
                    .background(disc)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Where on the ring a touch is, as 0–1 of the song: 0 at the bottom, going clockwise. */
private fun ringFraction(touch: Offset, size: IntSize): Float {
    val degrees = Math.toDegrees(atan2((touch.y - size.height / 2f).toDouble(), (touch.x - size.width / 2f).toDouble()))
    return (((degrees - 90 + 360) % 360) / 360).toFloat()
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
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outline),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        Row {
            Text(
                clock((fraction * duration).toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(clock(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}


/** Lyrics that follow the song; lines already sung turn orange, and the current one is kept in view. */
@Composable
private fun LyricsPane(lyrics: Lyrics?, position: Long) {
    when {
        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        lyrics.lines.isEmpty() -> Text(
            "No lyrics found for this song.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            val current = if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= position + 300 } else -1
            val listState = rememberLazyListState()
            LaunchedEffect(current) {
                if (current >= 0) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lyrics.lines) { i, line ->
                    val active = i == current
                    Text(
                        line.text.ifBlank { "♪" },
                        style = if (active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = if (lyrics.synced && i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.timeMs >= 0) { MusicPlayer.seekTo(line.timeMs) },
                    )
                }
                if (!lyrics.synced) {
                    item {
                        Text(
                            "Lyrics from LRCLIB (not timed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The songs after the current one. Tap to play; long-press to remove from the queue. */
@Composable
private fun QueuePane(queue: List<Song>, index: Int, vm: MainViewModel) {
    val next = queue.withIndex().drop(index + 1)
    if (next.isEmpty()) {
        Text(
            "Nothing up next. Start a radio from ⋮ to keep the music going.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(next, key = { _, item -> "${item.index}-${item.value.query}" }) { _, item ->
            val s = item.value
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        onClick = { MusicPlayer.skipTo(item.index) },
                        onLongClick = {
                            MusicPlayer.removeAt(item.index)
                            vm.toast("Removed from the queue")
                        },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(s.thumbnail, 52.dp, seed = s.title)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (s.durationMs > 0) {
                    Text(clock(s.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
