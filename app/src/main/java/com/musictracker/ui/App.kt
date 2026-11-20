@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musictracker.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LISTEN("Listen", Icons.Default.PlayArrow),
    LIBRARY("Library", Icons.Default.Favorite),
    STATS("Stats", Icons.Default.Star),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun MusicTrackerApp(
    onRequestAudio: () -> Unit,
    openPlayer: MutableStateFlow<Boolean>,
    vm: MainViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openSong by remember { mutableStateOf<Long?>(null) }
    var showRecognize by rememberSaveable { mutableStateOf(false) }
    val showPlayer by openPlayer.collectAsStateWithLifecycle()
    val plays by vm.plays.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val onOpenPlayer = { openPlayer.value = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(6.dp),
                        )
                        Text(
                            if (tab == Tab.HOME) "Music Tracker" else tab.label,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                MiniPlayer(onOpen = onOpenPlayer)
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    vm, plays,
                    onOpenSong = { openSong = it },
                    onGoSettings = { tab = Tab.SETTINGS },
                    onGoHistory = { tab = Tab.HISTORY },
                    onGoStats = { tab = Tab.STATS },
                    onRequestAudio = onRequestAudio,
                    onRecognize = { showRecognize = true },
                    updateAvailable = update as? Updater.State.Available,
                )
                Tab.LISTEN -> ListenScreen(vm, plays, onRecognize = { showRecognize = true }, onOpenPlayer = onOpenPlayer)
                Tab.LIBRARY -> LibraryScreen(plays, onOpenSong = { openSong = it }, onOpenPlayer = onOpenPlayer)
                Tab.STATS -> StatsScreen(plays, onOpenSong = { openSong = it })
                Tab.HISTORY -> HistoryScreen(vm, plays, onOpenSong = { openSong = it })
                Tab.SETTINGS -> SettingsScreen(vm, onRequestAudio = onRequestAudio)
            }
        }
    }

    openSong?.let { id ->
        SongDialog(trackId = id, plays = plays, vm = vm, onDismiss = { openSong = null }, onOpenPlayer = onOpenPlayer)
    }
    if (showRecognize) RecognizeScreen(vm, onDismiss = { showRecognize = false }, onOpenPlayer = onOpenPlayer)
    if (showPlayer) PlayerScreen(onDismiss = { openPlayer.value = false })
}

@Composable
fun RangeChips(selected: Range, onSelect: (Range) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Range.entries) { r ->
            FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.label) })
        }
    }
}

val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
val NoPadding = PaddingValues(0.dp)

/**
 * A song in a list: art, title, a grey detail line, and an orange play button when [onPlay] is set.
 * Lists that already pad and space their items pass [padding] = [NoPadding].
 */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    artUrl: String? = null,
    rank: Int? = null,
    onPlay: (() -> Unit)? = null,
    padding: PaddingValues = RowPadding,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
        }
        Artwork(artUrl, 56.dp, seed = title)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        trailing?.invoke()
        if (onPlay != null) PlayCircle(onPlay)
    }
}

@Composable
fun SongItem(
    song: SongStat,
    rank: Int? = null,
    detail: String? = null,
    onPlay: (() -> Unit)? = null,
    padding: PaddingValues = RowPadding,
    onClick: () -> Unit,
) {
    SongRow(
        title = (if (song.isFavourite) "♥ " else "") + song.title,
        subtitle = listOf(
            song.artist.ifBlank { "Unknown artist" },
            detail ?: "${song.plays} plays · ${Stats.duration(song.ms)}",
        ).joinToString("  |  "),
        onClick = onClick,
        rank = rank,
        onPlay = onPlay,
        padding = padding,
    )
}

/** The small orange round play button on song rows. */
@Composable
fun PlayCircle(onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
    }
}

/** A round tile with a name's first letter, for artists, apps and languages. */
@Composable
fun InitialAvatar(name: String, size: Dp) {
    val (from, to) = ArtPalette[Math.floorMod(name.hashCode(), ArtPalette.size)]
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(from, to))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "?" },
            style = if (size > 56.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Orange "Shuffle" next to a dark "Play", as on album pages. */
@Composable
fun ShufflePlayButtons(onShuffle: () -> Unit, onPlay: () -> Unit, playLabel: String = "Play") {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onShuffle, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Shuffle", modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(playLabel, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A bold section title, with an orange link such as "See all" on the right. */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onAction)
                    .padding(4.dp),
            )
        }
    }
}

/** Simple bar chart; [labels] are shown under some of the bars. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, color: Color = MaterialTheme.colorScheme.primary) {
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = size.height * (v / max)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + slot * 0.2f, size.height - h),
                    size = Size(slot * 0.6f, h),
                    cornerRadius = CornerRadius(slot * 0.3f),
                )
            }
        }
        val every = ((labels.size + 6) / 7).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    if (i % every == 0) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
