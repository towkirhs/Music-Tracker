@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow
import java.util.Locale

enum class GroupBy(val label: String, val keyOf: (PlayRow) -> String) {
    SONGS("Songs", { "" }),
    ARTISTS("Artists", { Stats.artistOf(it) }),
    APPS("Apps", { it.sourceApp }),
    LANGUAGES("Languages", { it.language }),
}

enum class SortBy(val label: String) {
    PLAYS("Most played"),
    TIME("Most time"),
    RECENT("Recently heard"),
    NEW("Newest finds"),
    SCORE("Favourite score"),
    AZ("A to Z"),
}

@Composable
fun LibraryScreen(plays: List<PlayRow>, onOpenSong: (Long) -> Unit) {
    var range by rememberSaveable { mutableStateOf(Range.ALL) }
    var groupBy by rememberSaveable { mutableStateOf(GroupBy.SONGS) }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.PLAYS) }
    var query by rememberSaveable { mutableStateOf("") }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    // Tapping an artist/app/language shows its songs.
    var filterGroup by rememberSaveable { mutableStateOf<GroupBy?>(null) }
    var filterValue by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }

    val filteredRows = remember(plays, filterGroup, filterValue) {
        val g = filterGroup
        if (g == null) plays else plays.filter { g.keyOf(it) == filterValue }
    }
    val showSongs = groupBy == GroupBy.SONGS || filterGroup != null
    val q = query.trim().lowercase(Locale.ROOT)

    val songs = remember(filteredRows, range, sortBy, q, favouritesOnly, showSongs) {
        if (!showSongs) emptyList() else Stats.songs(filteredRows, range)
            .filter { !favouritesOnly || it.isFavourite }
            .filter { q.isEmpty() || it.title.lowercase(Locale.ROOT).contains(q) || it.artist.lowercase(Locale.ROOT).contains(q) }
            .let { list ->
                when (sortBy) {
                    SortBy.PLAYS -> list.sortedByDescending { it.plays }
                    SortBy.TIME -> list.sortedByDescending { it.ms }
                    SortBy.RECENT -> list.sortedByDescending { it.lastPlayed }
                    SortBy.NEW -> list.sortedByDescending { it.firstSeen }
                    SortBy.SCORE -> list.sortedByDescending { it.score }
                    SortBy.AZ -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
                }
            }
    }
    val groups = remember(plays, range, groupBy, sortBy, q, showSongs) {
        if (showSongs) emptyList() else Stats.groups(Stats.inRange(plays, range), groupBy.keyOf)
            .filter { q.isEmpty() || it.name.lowercase(Locale.ROOT).contains(q) }
            .let { list ->
                when (sortBy) {
                    SortBy.PLAYS, SortBy.SCORE -> list.sortedByDescending { it.plays }
                    SortBy.AZ -> list.sortedBy { it.name.lowercase(Locale.ROOT) }
                    else -> list.sortedByDescending { it.ms }
                }
            }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search songs or artists") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Box(Modifier.padding(top = 8.dp)) { RangeChips(range) { range = it } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            items(GroupBy.entries) { g ->
                FilterChip(
                    selected = groupBy == g && filterGroup == null,
                    onClick = { groupBy = g; filterGroup = null },
                    label = { Text(g.label) },
                )
            }
            item {
                FilterChip(
                    selected = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly },
                    label = { Text("♥ Favourites") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            filterGroup?.let { g ->
                AssistChip(
                    onClick = { filterGroup = null },
                    label = { Text("${g.label.dropLast(1)}: $filterValue  ✕") },
                )
            }
            Box(Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("Sort: ${sortBy.label}") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortBy.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { sortBy = s; sortMenu = false })
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            if (showSongs) {
                if (songs.isEmpty()) item { Empty() }
                items(songs, key = { it.trackId }) { song ->
                    SongItem(song, detail = detailFor(song, sortBy)) { onOpenSong(song.trackId) }
                }
            } else {
                if (groups.isEmpty()) item { Empty() }
                items(groups, key = { it.name }) { g ->
                    ListItem(
                        modifier = Modifier.clickable { filterGroup = groupBy; filterValue = g.name },
                        headlineContent = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${g.songs} songs") },
                        trailingContent = {
                            Text(
                                "${g.plays} plays\n${Stats.duration(g.ms)}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun detailFor(song: SongStat, sortBy: SortBy): String = when (sortBy) {
    SortBy.RECENT -> "${Stats.date(song.lastPlayed)}\n${song.plays} plays"
    SortBy.NEW -> "found ${Stats.date(song.firstSeen)}\n${song.plays} plays"
    SortBy.SCORE -> "score ${song.score}\n${song.plays} plays"
    else -> "${song.plays} plays\n${Stats.duration(song.ms)}"
}

@Composable
private fun Empty() {
    Text(
        "No songs here yet.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
