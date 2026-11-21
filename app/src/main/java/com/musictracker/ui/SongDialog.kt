@file:OptIn(ExperimentalMaterial3Api::class)

package com.musictracker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Download
import com.musictracker.player.Downloads
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musictracker.data.PlayRow
import com.musictracker.data.SongText
import com.musictracker.player.MusicPlayer
import com.musictracker.player.Song

/** Song details: stats, rename (merges duplicates), language, favourite, delete. */
@Composable
fun SongDialog(trackId: Long, plays: List<PlayRow>, vm: MainViewModel, onDismiss: () -> Unit, onOpenPlayer: () -> Unit) {
    val rows = remember(plays, trackId) { plays.filter { it.trackId == trackId } }
    if (rows.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val first = rows.first()
    val context = LocalContext.current
    var title by remember(trackId) { mutableStateOf(first.title) }
    var artist by remember(trackId) { mutableStateOf(first.artist) }
    var language by remember(trackId) { mutableStateOf(first.language) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember(trackId) { mutableStateOf(false) }
    val apps = rows.groupingBy { it.sourceApp }.eachCount().entries
        .sortedByDescending { it.value }.joinToString { "${it.key} (${it.value})" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(null, 64.dp, seed = first.title)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(first.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${first.artist.ifBlank { "Unknown artist" }}  |  ${rows.size} plays · ${Stats.duration(rows.sumOf { it.playedMs })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = { vm.setFavourite(trackId, !first.isFavourite) }) {
                    Icon(
                        if (first.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (first.isFavourite) "Unheart" else "Heart",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                listOf(first.genre, first.language).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            Text(
                "First heard ${Stats.date(first.firstSeen)} · last ${Stats.date(rows.maxOf { it.startedAt })}\nHeard on: $apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            val onlineName = first.onlineTitle.isNotBlank() &&
                SongText.key(first.onlineTitle, first.onlineArtist) != SongText.key(first.title, first.artist)
            if (onlineName) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Found online as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                "${first.onlineTitle} · ${first.onlineArtist}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = {
                            vm.editTrack(trackId, first.onlineTitle, first.onlineArtist, first.language)
                            onDismiss()
                        }) { Text("Use this name") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outline)
            SheetAction(Icons.Default.PlayCircle, "Play here") {
                MusicPlayer.play(context, listOf(Song(null, first.title, first.artist, first.coverUrl.ifBlank { null })))
                onDismiss()
                onOpenPlayer()
            }
            SheetAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") {
                MusicPlayer.playNext(context, Song(null, first.title, first.artist, first.coverUrl.ifBlank { null }))
                vm.toast("Plays next")
            }
            SheetAction(Icons.Default.Download, "Download to this phone") {
                Downloads.download(context, listOf(Song(null, first.title, first.artist, first.coverUrl.ifBlank { null })))
                vm.toast("Downloading to Music/Music Tracker")
            }
            SheetAction(Icons.Default.Radio, "Start radio") {
                vm.startSongRadio(first.title, first.artist)
                onDismiss()
            }
            SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open on YouTube") {
                val url = SongText.youtubeUrl(title, artist)
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "No app or browser found to open YouTube", Toast.LENGTH_SHORT).show()
                }
            }
            SheetAction(Icons.Default.Edit, "Edit details") { editing = !editing }
            if (editing) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { val t = title; title = artist; artist = t }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Swap names", modifier = Modifier.padding(start = 8.dp))
                        }
                        Button(
                            enabled = title.isNotBlank(),
                            onClick = {
                                vm.editTrack(trackId, title, artist, language)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                    }
                }
            }
            SheetAction(Icons.Default.Delete, "Delete song and all its plays", danger = true) { confirmDelete = true }
        }
    }

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

@Composable
private fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.padding(start = 16.dp))
    }
}
