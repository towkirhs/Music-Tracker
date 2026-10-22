package com.musictracker.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Recent lyrics-recognition steps, shown in Settings so problems are visible. Memory only. */
object LyricsLog {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    fun add(message: String) {
        val line = LocalTime.now().format(fmt) + "  " + message
        _entries.value = (listOf(line) + _entries.value).take(40)
    }
}
