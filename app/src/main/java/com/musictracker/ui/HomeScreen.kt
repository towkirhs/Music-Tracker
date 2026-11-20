@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musictracker.data.PlayRow

@Composable
fun HomeScreen(
    vm: MainViewModel,
    plays: List<PlayRow>,
    onOpenSong: (Long) -> Unit,
    onGoSettings: () -> Unit,
    onGoHistory: () -> Unit,
    onGoStats: () -> Unit,
    onRequestAudio: () -> Unit,
    onRecognize: () -> Unit,
    updateAvailable: com.musictracker.update.Updater.State.Available?,
) {
    val context = LocalContext.current
    val listening by vm.listening.collectAsStateWithLifecycle()
    val lyricsOn by vm.lyricsOn.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val audioRunning by vm.audioRunning.collectAsStateWithLifecycle()
    val shazamOn by vm.shazamOn.collectAsStateWithLifecycle()
    // Unnamed reels can be recognised by Shazam alone, or by lyrics once the speech model is ready.
    val canRecognise = lyricsOn == true && (shazamOn || model is com.musictracker.lyrics.ModelManager.State.Ready)
    val toggle: (Boolean) -> Unit = { turnOn ->
        vm.setListening(turnOn)
        if (turnOn && canRecognise) onRequestAudio()
    }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val readerOn = remember(refresh) { Setup.labelReaderOn(context) }

    val today = remember(plays) { Stats.inRange(plays, Range.TODAY) }
    val streak = remember(plays) { Stats.streak(plays) }
    val recent = remember(plays) { plays.distinctBy { it.trackId }.take(12) }
    val weekRows = remember(plays) { Stats.inRange(plays, Range.WEEK) }
    val topSong = remember(plays) { Stats.songs(plays, Range.WEEK).maxByOrNull { it.plays } }
    val topArtists = remember(weekRows) {
        Stats.groups(weekRows, Stats::artistOf).sortedByDescending { it.plays }.take(10)
    }
    val on = listening == true

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggle(!on) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (on) Icons.Default.GraphicEq else Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .padding(12.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(if (on) "Listening" else "Stopped", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (on) "Logging songs from Reels, Shorts, TikTok and music apps until you stop it."
                            else "Tap to start. It keeps running until you stop it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(checked = on, onCheckedChange = { toggle(it) })
                }
            }
        }
        if (updateAvailable != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Update available: ${updateAvailable.release.name}",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Button(onClick = { vm.installUpdate(updateAvailable.release) }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Update now")
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("What's this song?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Name music around you or on this phone, like Shazam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Button(onClick = onRecognize, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Recognise", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (on && canRecognise && !audioRunning) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Lyrics recognition is paused. Allow audio so covers and \"Original audio\" reels can be named. " +
                                "Android asks again after the screen locks.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = onRequestAudio, modifier = Modifier.padding(top = 8.dp)) { Text("Allow audio") }
                    }
                }
            }
        }
        if (!readerOn) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Finish setup so songs from Reels, Shorts and TikTok can be read.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Button(onClick = onGoSettings, modifier = Modifier.padding(top = 8.dp)) { Text("Set up") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${today.map { it.trackId }.distinct().size}", "songs today", Modifier.weight(1f))
                StatTile(Stats.duration(today.sumOf { it.playedMs }), "listened today", Modifier.weight(1f))
                StatTile("$streak", if (streak == 1) "day streak" else "days streak", Modifier.weight(1f))
            }
        }
        topSong?.let { song ->
            item { TopSongCard(song, onClick = { onOpenSong(song.trackId) }) }
        }
        item { SectionTitle("Recently heard", action = if (plays.isEmpty()) null else "See all", onAction = onGoHistory) }
        if (plays.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Start listening and scroll some Reels: songs show up here after 10 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.trackId }) { row ->
                        Column(
                            Modifier
                                .width(128.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onOpenSong(row.trackId) },
                        ) {
                            Artwork(null, 128.dp, seed = row.title, shape = MaterialTheme.shapes.medium)
                            Text(
                                row.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                row.artist.ifBlank { "Unknown artist" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (topArtists.isNotEmpty()) {
            item { SectionTitle("Top artists this week", action = "See all", onAction = onGoStats) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(topArtists, key = { it.name }) { a ->
                        Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            InitialAvatar(a.name, 80.dp)
                            Text(
                                a.name,
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "${a.plays} plays",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (plays.isNotEmpty()) item { SectionTitle("Latest plays") }
        items(plays.take(15), key = { it.playId }) { row ->
            SongRow(
                padding = NoPadding,
                title = row.title,
                subtitle = listOf(row.artist.ifBlank { "Unknown artist" }, row.sourceApp).joinToString("  |  "),
                onClick = { onOpenSong(row.trackId) },
                trailing = {
                    Text(
                        Stats.time(row.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

/** The week's most played song, in an orange card. */
@Composable
private fun TopSongCard(song: SongStat, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(null, 72.dp, seed = song.title)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text("Most listened this week", style = MaterialTheme.typography.labelMedium)
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "${song.artist.ifBlank { "Unknown artist" }}  ·  ${song.plays} plays",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
