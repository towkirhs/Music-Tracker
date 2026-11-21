@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musictracker.data.DeviceMusic
import com.musictracker.player.MusicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class DeviceGroup(val label: String, val keyOf: (DeviceMusic.Track) -> String) {
    SONGS("Songs", { "" }),
    ARTISTS("Artists", { it.artist.ifBlank { "Unknown artist" } }),
    ALBUMS("Albums", { it.album.ifBlank { "Unknown album" } }),
    FOLDERS("Folders", { it.folder }),
}

/** Every music file on the phone (including downloads), by song, artist, album or folder. Plays offline. */
@Composable
fun DeviceMusicScreen(onDismiss: () -> Unit, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(DeviceMusic.canRead(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val tracks by produceState<List<DeviceMusic.Track>?>(null, allowed) {
        value = if (allowed) withContext(Dispatchers.IO) { DeviceMusic.all(context) } else emptyList()
    }
    var group by remember { mutableStateOf(DeviceGroup.SONGS) }
    var filter by remember { mutableStateOf<Pair<DeviceGroup, String>?>(null) }
    var query by remember { mutableStateOf("") }

    fun play(list: List<DeviceMusic.Track>, index: Int, shuffle: Boolean) {
        MusicPlayer.play(context, list.map { it.toSong() }, index, shuffle)
        onDismiss()
        onOpenPlayer()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("On this phone", style = MaterialTheme.typography.headlineSmall)
                }
                if (!allowed) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "To show and play the music files on your phone, Music Tracker needs permission to read your audio.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { ask.launch(DeviceMusic.permission) }) { Text("Allow") }
                    }
                    return@Column
                }
                val list = tracks
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = DeviceGroup.entries.indexOf(if (filter != null) DeviceGroup.SONGS else group),
                    edgePadding = 16.dp,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
                ) {
                    DeviceGroup.entries.forEach { g ->
                        Tab(
                            selected = group == g && filter == null,
                            onClick = { group = g; filter = null },
                            text = { Text(g.label, style = MaterialTheme.typography.titleSmall) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your music") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                filter?.let { (g, value) ->
                    InputChip(
                        selected = true,
                        onClick = { filter = null },
                        label = { Text("${g.label.dropLast(1)}: $value") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }

                val q = query.trim().lowercase(Locale.ROOT)
                val inFilter = filter?.let { (g, value) -> list.filter { g.keyOf(it) == value } } ?: list
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (filter != null || group == DeviceGroup.SONGS) {
                        val songs = inFilter.filter {
                            q.isEmpty() || it.title.lowercase(Locale.ROOT).contains(q) || it.artist.lowercase(Locale.ROOT).contains(q)
                        }
                        item {
                            Text(
                                "${songs.size} songs",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                            )
                        }
                        if (songs.isEmpty()) {
                            item {
                                Text(
                                    if (list.isEmpty()) "No music files on this phone yet. Songs you download show up here."
                                    else "No songs match.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        } else {
                            item {
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    ShufflePlayButtons(
                                        onShuffle = { play(songs, songs.indices.random(), shuffle = true) },
                                        onPlay = { play(songs, 0, shuffle = false) },
                                    )
                                }
                            }
                        }
                        itemsIndexed(songs, key = { _, t -> t.uri }) { i, t ->
                            SongRow(
                                title = t.title,
                                subtitle = listOf(t.artist.ifBlank { "Unknown artist" }, Stats.duration(t.durationMs)).joinToString("  |  "),
                                artUrl = t.coverUri,
                                onClick = { play(songs, i, shuffle = false) },
                            )
                        }
                    } else {
                        val groups = list.groupBy(group.keyOf).entries
                            .filter { q.isEmpty() || it.key.lowercase(Locale.ROOT).contains(q) }
                            .sortedBy { it.key.lowercase(Locale.ROOT) }
                        item {
                            Text(
                                "${groups.size} ${group.label.lowercase(Locale.ROOT)}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(groups, key = { it.key }) { (name, songs) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { filter = group to name; query = "" }
                                    .padding(RowPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (group == DeviceGroup.ALBUMS) Artwork(songs.first().coverUri, 56.dp, seed = name)
                                else InitialAvatar(name, 56.dp)
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp),
                                ) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${songs.size} songs  |  ${Stats.duration(songs.sumOf { it.durationMs })}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
