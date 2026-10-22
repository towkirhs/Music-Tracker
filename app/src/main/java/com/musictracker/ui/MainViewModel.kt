package com.musictracker.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musictracker.data.AppDatabase
import com.musictracker.data.LabelSample
import com.musictracker.data.PlayRow
import com.musictracker.data.Repo
import com.musictracker.export.Exporter
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportKind(val mime: String, val fileName: String, val done: String) {
    HISTORY_CSV("text/csv", "music-history.csv", "History saved"),
    SONGS_CSV("text/csv", "music-songs.csv", "Song list saved"),
    HTML_REPORT("text/html", "music-report.html", "Report saved"),
    BACKUP("application/json", "music-tracker-backup.json", "Backup saved"),
    LAB_CSV("text/csv", "label-lab.csv", "Label lab text saved"),
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).music()

    val plays: StateFlow<List<PlayRow>> = dao.observePlays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val samples: StateFlow<List<LabelSample>> = dao.observeSamples()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listening: StateFlow<Boolean?> = Prefs.listeningFlow(app)
    val labOn: StateFlow<Boolean?> = Prefs.labFlow(app)

    private val context get() = getApplication<Application>()

    fun setListening(on: Boolean) = Prefs.setListening(context, on)
    fun setLab(on: Boolean) = Prefs.setLab(context, on)

    fun setFavourite(trackId: Long, favourite: Boolean) {
        viewModelScope.launch { Repo.setFavourite(context, trackId, favourite) }
    }

    fun editTrack(trackId: Long, title: String, artist: String, language: String) {
        viewModelScope.launch { Repo.editTrack(context, trackId, title.trim(), artist.trim(), language) }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch { Repo.deleteTrack(context, trackId) }
    }

    fun deletePlay(row: PlayRow) {
        viewModelScope.launch { Repo.deletePlay(context, row.playId, row.trackId) }
    }

    fun clearSamples() {
        viewModelScope.launch { dao.clearSamples() }
    }

    fun export(kind: ExportKind, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (kind) {
                        ExportKind.HISTORY_CSV -> Exporter.historyCsv(context, uri)
                        ExportKind.SONGS_CSV -> Exporter.songsCsv(context, uri)
                        ExportKind.HTML_REPORT -> Exporter.htmlReport(context, uri)
                        ExportKind.BACKUP -> Exporter.backup(context, uri)
                        ExportKind.LAB_CSV -> Exporter.labCsv(context, uri)
                    }
                }
            }
            toast(if (result.isSuccess) kind.done else "Couldn't save: ${result.exceptionOrNull()?.message}")
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Exporter.restore(context, uri) } }
            toast(
                result.fold(
                    onSuccess = { "Restored $it plays" },
                    onFailure = { "That file isn't a Music Tracker backup" },
                ),
            )
        }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
