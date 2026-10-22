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

@Composable
fun HistoryScreen(vm: MainViewModel, plays: List<PlayRow>, onOpenSong: (Long) -> Unit) {
    val byDay = remember(plays) { plays.groupBy { Stats.day(it.startedAt) } }
    var toDelete by remember { mutableStateOf<PlayRow?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (plays.isEmpty()) {
            item { Text("No plays yet.", modifier = Modifier.padding(vertical = 24.dp)) }
        }
        byDay.forEach { (day, rows) ->
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
