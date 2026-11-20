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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow
import com.musictracker.player.MusicPlayer
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
fun LibraryScreen(plays: List<PlayRow>, onOpenSong: (Long) -> Unit, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
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

    Column {
        PrimaryScrollableTabRow(
            selectedTabIndex = GroupBy.entries.indexOf(if (filterGroup != null) GroupBy.SONGS else groupBy),
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
        ) {
            GroupBy.entries.forEach { g ->
                Tab(
                    selected = groupBy == g && filterGroup == null,
                    onClick = { groupBy = g; filterGroup = null },
                    text = { Text(g.label, style = MaterialTheme.typography.titleSmall) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs or artists") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(Range.entries) { r ->
                FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) })
            }
            item {
                FilterChip(
                    selected = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly },
                    label = { Text("♥ Favourites") },
                )
            }
        }
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val count = if (showSongs) "${songs.size} songs" else "${groups.size} ${groupBy.label.lowercase(Locale.ROOT)}"
            Text(count, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(sortBy.label)
                    Icon(Icons.Default.SwapVert, contentDescription = "Sort", modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortBy.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = { sortBy = s; sortMenu = false },
                            trailingIcon = {
                                RadioButton(selected = sortBy == s, onClick = { sortBy = s; sortMenu = false })
                            },
                        )
                    }
                }
            }
        }
        filterGroup?.let { g ->
            InputChip(
                selected = true,
                onClick = { filterGroup = null },
                label = { Text("${g.label.dropLast(1)}: $filterValue") },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            if (showSongs) {
                if (songs.isEmpty()) item { Empty() }
                if (songs.isNotEmpty()) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ShufflePlayButtons(
                                onShuffle = {
                                    MusicPlayer.play(context, songs.toSongs(), songs.indices.random(), shuffle = true)
                                    onOpenPlayer()
                                },
                                onPlay = {
                                    MusicPlayer.play(context, songs.toSongs())
                                    onOpenPlayer()
                                },
                            )
                        }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.trackId }) { i, song ->
                    SongItem(
                        song,
                        detail = detailFor(song, sortBy),
                        onPlay = {
                            MusicPlayer.play(context, songs.toSongs(), i)
                            onOpenPlayer()
                        },
                    ) { onOpenSong(song.trackId) }
                }
            } else {
                if (groups.isEmpty()) item { Empty() }
                items(groups, key = { it.name }) { g ->
                    ListItem(
                        modifier = Modifier.clickable { filterGroup = groupBy; filterValue = g.name },
                        leadingContent = { InitialAvatar(g.name, 56.dp) },
                        headlineContent = {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                "${g.songs} songs  |  ${g.plays} plays  |  ${Stats.duration(g.ms)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

private fun detailFor(song: SongStat, sortBy: SortBy): String = when (sortBy) {
    SortBy.RECENT -> "${Stats.date(song.lastPlayed)} · ${song.plays} plays"
    SortBy.NEW -> "found ${Stats.date(song.firstSeen)} · ${song.plays} plays"
    SortBy.SCORE -> "score ${song.score} · ${song.plays} plays"
    else -> "${song.plays} plays · ${Stats.duration(song.ms)}"
}

@Composable
private fun Empty() {
    Text(
        "No songs here yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
