@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow
import com.musictracker.data.SongText

/** Song details: stats, rename (merges duplicates), language, favourite, delete. */
@Composable
fun SongDialog(trackId: Long, plays: List<PlayRow>, vm: MainViewModel, onDismiss: () -> Unit) {
    val rows = remember(plays, trackId) { plays.filter { it.trackId == trackId } }
    if (rows.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val first = rows.first()
    var title by remember(trackId) { mutableStateOf(first.title) }
    var artist by remember(trackId) { mutableStateOf(first.artist) }
    var language by remember(trackId) { mutableStateOf(first.language) }
    var confirmDelete by remember { mutableStateOf(false) }
    val apps = rows.groupingBy { it.sourceApp }.eachCount().entries
        .sortedByDescending { it.value }.joinToString { "${it.key} (${it.value})" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Song") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${rows.size} plays · ${Stats.duration(rows.sumOf { it.playedMs })}\n" +
                        "First heard ${Stats.date(first.firstSeen)} · last ${Stats.date(rows.maxOf { it.startedAt })}\n" +
                        "Heard on: $apps",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val t = title; title = artist; artist = t }) { Text("Swap") }
                    OutlinedButton(onClick = { vm.setFavourite(trackId, !first.isFavourite) }) {
                        Text(if (first.isFavourite) "♥ Unheart" else "♡ Heart")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SongText.LANGUAGES.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { language = lang },
                            label = { Text(lang) },
                        )
                    }
                }
                Text(
                    "Renaming to a name you already have merges the two songs.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Delete song and all its plays", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    vm.editTrack(trackId, title, artist, language)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this song?") },
            text = { Text("Removes ${first.title} and its ${rows.size} plays.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTrack(trackId)
                    confirmDelete = false
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
