@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.Favorite),
    STATS("Stats", Icons.Default.Star),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun MusicTrackerApp(onRequestAudio: () -> Unit, vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openSong by remember { mutableStateOf<Long?>(null) }
    val plays by vm.plays.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == Tab.HOME) "Music Tracker" else tab.label) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm, plays, onOpenSong = { openSong = it }, onGoSettings = { tab = Tab.SETTINGS }, onRequestAudio = onRequestAudio)
                Tab.LIBRARY -> LibraryScreen(plays, onOpenSong = { openSong = it })
                Tab.STATS -> StatsScreen(plays, onOpenSong = { openSong = it })
                Tab.HISTORY -> HistoryScreen(vm, plays, onOpenSong = { openSong = it })
                Tab.SETTINGS -> SettingsScreen(vm, onRequestAudio = onRequestAudio)
            }
        }
    }

    openSong?.let { id -> SongDialog(trackId = id, plays = plays, vm = vm, onDismiss = { openSong = null }) }
}

@Composable
fun RangeChips(selected: Range, onSelect: (Range) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Range.entries) { r ->
            FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.label) })
        }
    }
}

@Composable
fun SongItem(song: SongStat, rank: Int? = null, detail: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = if (rank != null) {
            { Text("$rank", style = MaterialTheme.typography.titleMedium) }
        } else {
            null
        },
        headlineContent = {
            Text(
                (if (song.isFavourite) "♥ " else "") + song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                listOf(song.artist.ifBlank { "Unknown artist" }, song.language).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                detail ?: "${song.plays} plays\n${Stats.duration(song.ms)}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
            )
        },
    )
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
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
                drawRect(
                    color = color,
                    topLeft = Offset(i * slot + slot * 0.15f, size.height - h),
                    size = Size(slot * 0.7f, h),
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
