@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musictracker.data.Unidentified

@Composable
fun HistoryScreen(vm: MainViewModel, plays: List<PlayRow>, onOpenSong: (Long) -> Unit) {
    val byDay = remember(plays) { plays.groupBy { Stats.day(it.startedAt) } }
    var toDelete by remember { mutableStateOf<PlayRow?>(null) }
    val unidentified by vm.unidentified.collectAsStateWithLifecycle()
    var showInbox by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Unidentified?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showInbox, onClick = { showInbox = false }, label = { Text("Plays") })
                FilterChip(
                    selected = showInbox,
                    onClick = { showInbox = true },
                    label = { Text("Unidentified (${unidentified.size})") },
                )
            }
        }
        if (showInbox) {
            if (unidentified.isEmpty()) {
                item {
                    Text(
                        "Nothing here. Songs the app hears but can't name show up here with their lyrics.",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(unidentified, key = { "u-" + it.id }) { item ->
                UnidentifiedCard(
                    item = item,
                    onUseGuess = { vm.nameUnidentified(item, item.guessTitle, item.guessArtist) },
                    onName = { naming = item },
                    onDismiss = { vm.dismissUnidentified(item) },
                )
            }
        }
        if (!showInbox && plays.isEmpty()) {
            item { Text("No plays yet.", modifier = Modifier.padding(vertical = 24.dp)) }
        }
        if (!showInbox) byDay.forEach { (day, rows) ->
            item(key = "day-$day") {
                Text(
                    "${Stats.dayHeader(day)} · ${rows.size} plays · ${Stats.duration(rows.sumOf { it.playedMs })}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(rows, key = { it.playId }) { row ->
                ListItem(
                    modifier = Modifier.clickable { onOpenSong(row.trackId) },
                    headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            listOf(
                                row.artist.ifBlank { "Unknown artist" },
                                row.sourceApp,
                                Stats.time(row.startedAt),
                                Stats.duration(row.playedMs),
                            ).joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { toDelete = row }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete this play")
                        }
                    },
                )
            }
        }
    }

    naming?.let { item ->
        NameDialog(
            initialTitle = item.guessTitle,
            initialArtist = item.guessArtist,
            onSave = { title, artist -> vm.nameUnidentified(item, title, artist); naming = null },
            onDismiss = { naming = null },
        )
    }

    toDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this play?") },
            text = { Text("${row.title} at ${Stats.time(row.startedAt)}. Use this for wrong detections.") },
            confirmButton = { TextButton(onClick = { vm.deletePlay(row); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UnidentifiedCard(item: Unidentified, onUseGuess: () -> Unit, onName: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${item.sourceApp} · ${Stats.date(item.capturedAt)} ${Stats.time(item.capturedAt)}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (item.reelLabel.isNotBlank()) Text(item.reelLabel, style = MaterialTheme.typography.bodySmall)
            Text(
                "\u201C" + item.lyrics + "\u201D",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.guessTitle.isNotBlank()) {
                Text(
                    "Genius guess: ${item.guessTitle}" + if (item.guessArtist.isNotBlank()) " \u2014 ${item.guessArtist}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.guessTitle.isNotBlank()) FilledTonalButton(onClick = onUseGuess) { Text("Use guess") }
                OutlinedButton(onClick = onName) { Text("Name it") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun NameDialog(initialTitle: String, initialArtist: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this song") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist (optional)") }, singleLine = true)
                Text(
                    "It's added to your library, and lyrics matching will recognise it next time.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, artist) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
