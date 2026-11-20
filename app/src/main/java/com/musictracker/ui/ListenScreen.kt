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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musictracker.data.PlayRow
import com.musictracker.player.MusicPlayer
import com.musictracker.player.Song

/** Library songs as a play queue (the player finds each one on YouTube Music when it starts). */
fun List<SongStat>.toSongs(): List<Song> = map { Song(videoId = null, title = it.title, artist = it.artist) }

/** The built-in player's home: search YouTube Music, play your library, recognise a song. */
@Composable
fun ListenScreen(vm: MainViewModel, plays: List<PlayRow>, onRecognize: () -> Unit, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val search by vm.search.collectAsStateWithLifecycle()
    val songs = remember(plays) { Stats.songs(plays, Range.ALL) }
    val mostPlayed = remember(songs) { songs.sortedByDescending { it.plays } }
    val favourites = remember(songs) { songs.filter { it.isFavourite } }
    val recent = remember(songs) { songs.sortedByDescending { it.lastPlayed } }

    fun play(list: List<SongStat>, shuffle: Boolean) {
        MusicPlayer.play(context, list.toSongs(), if (shuffle) list.indices.random() else 0, shuffle)
        onOpenPlayer()
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("What's this song?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Recognise music playing around you or on this phone, then play it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = onRecognize,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Recognise a song", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs to play") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    vm.searchSongs(query)
                }),
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        TextButton(onClick = { query = ""; vm.clearSearch() }) { Text("Clear") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (search.query.isNotEmpty()) {
            if (search.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            search.error?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            if (!search.loading && search.error == null && search.results.isEmpty()) {
                item { Text("No songs found.", style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(search.results, key = { i, s -> "r$i-${s.videoId}" }) { i, song ->
                SongResultItem(
                    song = song,
                    onPlay = {
                        MusicPlayer.play(context, search.results, i)
                        onOpenPlayer()
                    },
                    onQueue = { MusicPlayer.addToQueue(context, song); vm.toast("Added to queue") },
                )
            }
        } else {
            item { SectionTitle("Play your library") }
            item {
                if (songs.isEmpty()) {
                    Text(
                        "Songs you hear show up here, ready to play. Search above to play anything now.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShufflePlayButtons(
                            onShuffle = { play(songs, true) },
                            onPlay = { play(mostPlayed, false) },
                            playLabel = "Most played",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = { play(favourites, false) },
                                enabled = favourites.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("♥ Favourites") }
                            FilledTonalButton(onClick = { play(recent, false) }, modifier = Modifier.weight(1f)) {
                                Text("Recently heard")
                            }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) item { SectionTitle("Recently heard") }
            itemsIndexed(recent.take(20), key = { _, s -> "h" + s.trackId }) { i, s ->
                SongRow(
                    padding = NoPadding,
                    title = s.title,
                    subtitle = s.artist.ifBlank { "Unknown artist" },
                    onClick = {
                        MusicPlayer.play(context, recent.take(50).toSongs(), i)
                        onOpenPlayer()
                    },
                    trailing = {
                        IconButton(onClick = {
                            MusicPlayer.addToQueue(context, Song(null, s.title, s.artist))
                            vm.toast("Added to queue")
                        }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue") }
                    },
                )
            }
        }
    }
}

@Composable
private fun SongResultItem(song: Song, onPlay: () -> Unit, onQueue: () -> Unit) {
    SongRow(
        padding = NoPadding,
        title = song.title,
        subtitle = listOfNotNull(
            song.artist.ifBlank { null },
            song.durationMs.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) },
        ).joinToString("  |  "),
        artUrl = song.thumbnail,
        onClick = onPlay,
        trailing = {
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue") }
        },
    )
}
