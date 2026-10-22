package com.musictracker.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.musictracker.data.AppDatabase
import com.musictracker.data.Play
import com.musictracker.data.PlayRow
import com.musictracker.data.Track
import com.musictracker.ui.Range
import com.musictracker.ui.Stats
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Files you can save to your phone: CSV (opens in Excel), an HTML report (print it to PDF), and a full backup. */
object Exporter {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun write(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private fun fmt(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(stamp)

    /** Every play as one row. The UTF-8 marker makes Excel show Bangla and Hindi correctly. */
    suspend fun historyCsv(context: Context, uri: Uri) {
        val rows = AppDatabase.get(context).music().allPlayRows()
        val sb = StringBuilder("﻿")
        sb.append("Date and time,Title,Artist,Language,Seconds listened,App,Detected by,Favourite\n")
        rows.forEach {
            sb.append(
                listOf(
                    fmt(it.startedAt), it.title, it.artist, it.language,
                    (it.playedMs / 1000).toString(), it.sourceApp, it.detector, if (it.isFavourite) "yes" else "",
                ).joinToString(",") { cell -> csvCell(cell) },
            ).append('\n')
        }
        write(context, uri, sb.toString())
    }

    /** One row per song with totals. */
    suspend fun songsCsv(context: Context, uri: Uri) {
        val rows = AppDatabase.get(context).music().allPlayRows()
        val songs = Stats.songs(rows, Range.ALL).sortedByDescending { it.plays }
        val sb = StringBuilder("﻿")
        sb.append("Title,Artist,Language,Plays,Minutes listened,First heard,Last heard,Most heard on,Favourite score,Hearted\n")
        songs.forEach {
            sb.append(
                listOf(
                    it.title, it.artist, it.language, it.plays.toString(), (it.ms / 60_000).toString(),
                    fmt(it.firstSeen), fmt(it.lastPlayed), it.topSource, it.score.toString(),
                    if (it.isFavourite) "yes" else "",
                ).joinToString(",") { cell -> csvCell(cell) },
            ).append('\n')
        }
        write(context, uri, sb.toString())
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A readable report. Open it in Chrome and use Share → Print → Save as PDF for a PDF. */
    suspend fun htmlReport(context: Context, uri: Uri) {
        val rows = AppDatabase.get(context).music().allPlayRows()
        val songs = Stats.songs(rows, Range.ALL).sortedByDescending { it.plays }
        val artists = Stats.groups(rows, Stats::artistOf)
        val languages = Stats.groups(rows) { it.language }
        val total = rows.sumOf { it.playedMs }
        val html = buildString {
            append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>")
            append("<title>Music Tracker report</title><style>")
            append("body{font-family:sans-serif;margin:24px;color:#222}table{border-collapse:collapse;width:100%;margin:12px 0 28px}")
            append("th,td{border-bottom:1px solid #ddd;padding:6px 8px;text-align:left;font-size:14px}th{background:#f3f3f3}")
            append("td.n{text-align:right}h1{margin-bottom:4px}.sub{color:#666}</style></head><body>")
            append("<h1>Music Tracker report</h1><p class='sub'>Made ${esc(fmt(System.currentTimeMillis()))}</p>")
            append("<p><b>${rows.size}</b> plays · <b>${songs.size}</b> songs · <b>${artists.size}</b> artists · <b>${esc(Stats.duration(total))}</b> listened</p>")
            append("<h2>Languages</h2><table><tr><th>Language</th><th>Plays</th><th>Time</th></tr>")
            languages.forEach { append("<tr><td>${esc(it.name)}</td><td class='n'>${it.plays}</td><td class='n'>${esc(Stats.duration(it.ms))}</td></tr>") }
            append("</table><h2>Top songs</h2><table><tr><th>#</th><th>Song</th><th>Artist</th><th>Plays</th><th>Time</th></tr>")
            songs.take(100).forEachIndexed { i, s ->
                append("<tr><td>${i + 1}</td><td>${esc(s.title)}</td><td>${esc(s.artist)}</td><td class='n'>${s.plays}</td><td class='n'>${esc(Stats.duration(s.ms))}</td></tr>")
            }
            append("</table><h2>Top artists</h2><table><tr><th>Artist</th><th>Songs</th><th>Plays</th><th>Time</th></tr>")
            artists.take(50).forEach {
                append("<tr><td>${esc(it.name)}</td><td class='n'>${it.songs}</td><td class='n'>${it.plays}</td><td class='n'>${esc(Stats.duration(it.ms))}</td></tr>")
            }
            append("</table><h2>History</h2><table><tr><th>When</th><th>Song</th><th>Artist</th><th>App</th><th>Seconds</th></tr>")
            rows.forEach { r: PlayRow ->
                append("<tr><td>${esc(fmt(r.startedAt))}</td><td>${esc(r.title)}</td><td>${esc(r.artist)}</td><td>${esc(r.sourceApp)}</td><td class='n'>${r.playedMs / 1000}</td></tr>")
            }
            append("</table></body></html>")
        }
        write(context, uri, html)
    }

    /** Everything in one file, to restore later with [restore]. */
    suspend fun backup(context: Context, uri: Uri) {
        val dao = AppDatabase.get(context).music()
        val tracks = JSONArray()
        dao.allTracks().forEach {
            tracks.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("artist", it.artist)
                    .put("normalizedKey", it.normalizedKey).put("language", it.language)
                    .put("labelRaw", it.labelRaw).put("firstSeen", it.firstSeen).put("isFavourite", it.isFavourite),
            )
        }
        val plays = JSONArray()
        dao.allPlays().forEach {
            plays.put(
                JSONObject()
                    .put("id", it.id).put("trackId", it.trackId).put("startedAt", it.startedAt)
                    .put("playedMs", it.playedMs).put("sourceApp", it.sourceApp).put("detector", it.detector),
            )
        }
        val root = JSONObject().put("app", "Music Tracker").put("version", 1).put("tracks", tracks).put("plays", plays)
        write(context, uri, root.toString())
    }

    /** Replaces the library with the contents of a backup file. Returns the number of plays restored. */
    suspend fun restore(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        val tracksJson = root.getJSONArray("tracks")
        val playsJson = root.getJSONArray("plays")
        val tracks = (0 until tracksJson.length()).map { i ->
            val o = tracksJson.getJSONObject(i)
            Track(
                id = o.getLong("id"), title = o.getString("title"), artist = o.getString("artist"),
                normalizedKey = o.getString("normalizedKey"), language = o.getString("language"),
                labelRaw = o.optString("labelRaw"), firstSeen = o.getLong("firstSeen"),
                isFavourite = o.optBoolean("isFavourite"),
            )
        }
        val plays = (0 until playsJson.length()).map { i ->
            val o = playsJson.getJSONObject(i)
            Play(
                id = o.getLong("id"), trackId = o.getLong("trackId"), startedAt = o.getLong("startedAt"),
                playedMs = o.getLong("playedMs"), sourceApp = o.getString("sourceApp"), detector = o.getString("detector"),
            )
        }
        val db = AppDatabase.get(context)
        db.withTransaction {
            val dao = db.music()
            dao.clearPlays()
            dao.clearTracks()
            dao.insertTracks(tracks)
            dao.insertPlays(plays)
        }
        return plays.size
    }

    /** The Label lab's raw screen text, to share when tuning the label reader. */
    suspend fun labCsv(context: Context, uri: Uri) {
        val samples = AppDatabase.get(context).music().allSamples()
        val sb = StringBuilder("﻿")
        sb.append("Time,App,View id,Text\n")
        samples.forEach {
            sb.append(listOf(fmt(it.capturedAt), it.sourceApp, it.viewId, it.content).joinToString(",") { c -> csvCell(c) })
                .append('\n')
        }
        write(context, uri, sb.toString())
    }
}
