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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontWeight
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
    onRequestAudio: () -> Unit,
) {
    val context = LocalContext.current
    val listening by vm.listening.collectAsStateWithLifecycle()
    val lyricsOn by vm.lyricsOn.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val audioRunning by vm.audioRunning.collectAsStateWithLifecycle()
    val toggle: (Boolean) -> Unit = { turnOn ->
        vm.setListening(turnOn)
        if (turnOn && lyricsOn == true && model is com.musictracker.lyrics.ModelManager.State.Ready) onRequestAudio()
    }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val readerOn = remember(refresh) { Setup.labelReaderOn(context) }

    val today = remember(plays) { Stats.inRange(plays, Range.TODAY) }
    val streak = remember(plays) { Stats.streak(plays) }
    val on = listening == true

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggle(!on) },
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (on) "Listening" else "Stopped",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (on) "Logging songs from Reels, Shorts, TikTok and music apps until you stop it."
                            else "Tap to start. It keeps running until you stop it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = on, onCheckedChange = { toggle(it) })
                }
            }
        }
        if (on && lyricsOn == true && model is com.musictracker.lyrics.ModelManager.State.Ready && !audioRunning) {
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
        item { SectionTitle("Recently heard") }
        if (plays.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Start listening and scroll some Reels: songs show up here after 10 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(plays.take(15), key = { it.playId }) { row ->
            ListItem(
                modifier = Modifier.clickable { onOpenSong(row.trackId) },
                headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOf(row.artist.ifBlank { "Unknown artist" }, row.sourceApp).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = { Text(Stats.time(row.startedAt), style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}
