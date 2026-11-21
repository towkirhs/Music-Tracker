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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.musictracker.online.LastFm
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
fun List<SongStat>.toSongs(): List<Song> = map { Song(videoId = null, title = it.title, artist = it.artist, thumbnail = it.coverUrl.ifBlank { null }) }

/** The built-in player's home: search YouTube Music, play your library, recognise a song. */
@Composable
fun ListenScreen(
    vm: MainViewModel,
    plays: List<PlayRow>,
    searchRequest: Int,
    onRecognize: () -> Unit,
    onOpenPlayer: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchRequest) { if (searchRequest > 0) searchFocus.requestFocus() }
    val discover by vm.discover.collectAsStateWithLifecycle()
    val radioName by vm.radioName.collectAsStateWithLifecycle()
    val ytSignedIn by vm.ytSignedIn.collectAsStateWithLifecycle()
    val ytLibrary by vm.ytLibrary.collectAsStateWithLifecycle()
    val openPlaylist by vm.openPlaylist.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadDiscover() }
    LaunchedEffect(ytSignedIn) { vm.loadYtLibrary() }
    var showDevice by remember { mutableStateOf(false) }
    if (showDevice) DeviceMusicScreen(onDismiss = { showDevice = false }, onOpenPlayer = onOpenPlayer)
    openPlaylist?.let { PlaylistScreen(it, vm, onDismiss = vm::closePlaylist, onOpenPlayer = onOpenPlayer) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus),
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
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDevice = true },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        ) {
                            Text("On this phone", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "All music files on your phone, and songs you download. Plays without internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            if (ytSignedIn) ytMusicItems(ytLibrary, vm)
            discoverItems(discover, radioName, vm, onGoSettings, onOpenPlayer)
            if (recent.isNotEmpty()) item { SectionTitle("Recently heard") }
            itemsIndexed(recent.take(20), key = { _, s -> "h" + s.trackId }) { i, s ->
                SongRow(
                    padding = NoPadding,
                    title = s.title,
                    subtitle = s.artist.ifBlank { "Unknown artist" },
                    artUrl = s.coverUrl,
                    onClick = {
                        MusicPlayer.play(context, recent.take(50).toSongs(), i)
                        onOpenPlayer()
                    },
                    trailing = {
                        IconButton(onClick = {
                            MusicPlayer.addToQueue(context, Song(null, s.title, s.artist, s.coverUrl.ifBlank { null }))
                            vm.toast("Added to queue")
                        }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue") }
                    },
                )
            }
        }
    }
}

/** Your YouTube Music liked songs and playlists. */
private fun LazyListScope.ytMusicItems(library: YtLibraryState, vm: MainViewModel) {
    item { SectionTitle("Your YouTube Music") }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PlaylistTile("Liked songs", "Your likes", null) { vm.showPlaylist(LIKED, "Liked songs", null) } }
            items(library.playlists, key = { it.browseId }) { p ->
                PlaylistTile(p.title, p.subtitle, p.thumbnail) { vm.showPlaylist(p.browseId, p.title, p.thumbnail) }
            }
        }
    }
    if (library.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    library.error?.let { message ->
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.loadYtLibrary(force = true) }) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun PlaylistTile(title: String, subtitle: String, thumbnail: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(128.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
    ) {
        Artwork(thumbnail, 128.dp, seed = title, shape = MaterialTheme.shapes.medium)
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Radio and Last.fm suggestions, between your library buttons and your recent songs. */
private fun LazyListScope.discoverItems(
    discover: DiscoverState,
    radioName: String?,
    vm: MainViewModel,
    onGoSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    item { SectionTitle("Radio") }
    if (discover.needsKey) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Discover with Last.fm", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Radio and suggestions based on what you listen to. Add your free Last.fm API key in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(onClick = onGoSettings, modifier = Modifier.padding(top = 12.dp)) { Text("Open Settings") }
                }
            }
        }
        return
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { RadioCard("Your radio", "From your top songs", highlight = true) { vm.startMyRadio() } }
            item { RadioCard("Bangla radio", "Top Bangla songs") { vm.startTagRadio("Bangla", "bangla") } }
            item { RadioCard("Hindi radio", "Top Hindi songs") { vm.startTagRadio("Hindi", "hindi") } }
        }
    }
    if (radioName != null) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "Playing $radioName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                TextButton(onClick = onOpenPlayer) { Text("Open player") }
            }
        }
    }
    if (discover.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    discover.error?.let { message ->
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.loadDiscover(force = true) }) { Text("Try again") }
            }
        }
    }
    discover.basedOn?.let { seed ->
        item {
            SectionTitle("Because you listened to ${seed.title}", action = "Radio") {
                vm.startSongRadio(seed.title, seed.artist)
            }
        }
        suggestionRows("similar", discover.similar.take(6), vm, onOpenPlayer)
    }
    if (discover.artists.isNotEmpty()) {
        item { SectionTitle("Artists for you") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(discover.artists, key = { it }) { artist ->
                    Column(
                        Modifier
                            .width(80.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { vm.startArtistRadio(artist) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InitialAvatar(artist, 80.dp)
                        Text(
                            artist,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text("Radio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (discover.bangla.isNotEmpty()) {
        item { SectionTitle("Top Bangla", action = "Radio") { vm.startTagRadio("Bangla", "bangla") } }
        suggestionRows("bangla", discover.bangla.take(5), vm, onOpenPlayer)
    }
    if (discover.hindi.isNotEmpty()) {
        item { SectionTitle("Top Hindi", action = "Radio") { vm.startTagRadio("Hindi", "hindi") } }
        suggestionRows("hindi", discover.hindi.take(5), vm, onOpenPlayer)
    }
}

/** Suggested songs; tapping one plays the list from there. */
private fun LazyListScope.suggestionRows(prefix: String, tracks: List<LastFm.Track>, vm: MainViewModel, onOpenPlayer: () -> Unit) {
    val songs = tracks.map { Song(videoId = null, title = it.title, artist = it.artist) }
    itemsIndexed(songs, key = { i, s -> "$prefix$i-${s.query}" }) { i, song ->
        val context = LocalContext.current
        SongRow(
            padding = NoPadding,
            title = song.title,
            subtitle = song.artist.ifBlank { "Unknown artist" },
            onClick = {
                MusicPlayer.play(context, songs, i)
                onOpenPlayer()
            },
            trailing = {
                IconButton(onClick = {
                    MusicPlayer.addToQueue(context, song)
                    vm.toast("Added to queue")
                }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue") }
            },
        )
    }
}

@Composable
private fun RadioCard(title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    Card(
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .width(156.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                Icons.Default.Radio,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
