@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow

@Composable
fun StatsScreen(plays: List<PlayRow>, onOpenSong: (Long) -> Unit) {
    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    val rows = remember(plays, range) { Stats.inRange(plays, range) }
    val songs = remember(plays, range) { Stats.songs(plays, range) }
    val topSongs = remember(songs) { songs.sortedByDescending { it.plays }.take(10) }
    val artists = remember(rows) { Stats.groups(rows, Stats::artistOf) }
    val languages = remember(rows) { Stats.groups(rows) { it.language } }
    val apps = remember(rows) { Stats.groups(rows) { it.sourceApp } }
    val since = range.since()
    val newSongs = songs.count { it.firstSeen >= since }
    val days = if (range == Range.TODAY || range == Range.WEEK) 7 else 30
    val daily = remember(plays, days) { Stats.dailyMinutes(plays, days) }
    val hourly = remember(rows) { Stats.hourly(rows) }
    val totalMs = rows.sumOf { it.playedMs }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { RangeChips(range) { range = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                StatTile(Stats.duration(totalMs), "listened", Modifier.weight(1f))
                StatTile("${rows.size}", "plays", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                StatTile("${songs.size}", "songs", Modifier.weight(1f))
                StatTile("$newSongs", "new finds", Modifier.weight(1f))
                StatTile("${artists.size}", "artists", Modifier.weight(1f))
            }
        }
        item {
            Column {
                SectionTitle("Minutes per day")
                Card { Column(Modifier.padding(12.dp)) { BarChart(daily.map { it.second }, daily.map { it.first }) } }
            }
        }
        item { SectionTitle("Top songs") }
        if (topSongs.isEmpty()) item { Text("No plays in this period.") }
        itemsIndexed(topSongs, key = { _, s -> s.trackId }) { i, song ->
            SongItem(song, rank = i + 1) { onOpenSong(song.trackId) }
        }
        item { SectionTitle("Top artists") }
        itemsIndexed(artists.take(5), key = { _, a -> "artist-" + a.name }) { _, a ->
            ShareRow(a.name, "${a.plays} plays · ${Stats.duration(a.ms)}", a.ms, totalMs)
        }
        item { SectionTitle("Languages") }
        itemsIndexed(languages, key = { _, l -> "lang-" + l.name }) { _, l ->
            ShareRow(l.name, "${l.songs} songs · ${Stats.duration(l.ms)}", l.ms, totalMs)
        }
        item { SectionTitle("Apps") }
        itemsIndexed(apps, key = { _, a -> "app-" + a.name }) { _, a ->
            ShareRow(a.name, "${a.plays} plays · ${Stats.duration(a.ms)}", a.ms, totalMs)
        }
        item {
            Column {
                SectionTitle("Listening clock (minutes by hour)")
                Card {
                    Column(Modifier.padding(12.dp)) {
                        BarChart(hourly, (0..23).map { h -> if (h % 4 == 0) "$h" else "" })
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareRow(name: String, detail: String, part: Long, total: Long) {
    val fraction = if (total > 0) (part.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}
