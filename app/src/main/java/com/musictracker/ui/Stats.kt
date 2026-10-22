package com.musictracker.ui

import com.musictracker.data.PlayRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time ranges every list and chart can be filtered by. */
enum class Range(val label: String) {
    TODAY("Today"), WEEK("7 days"), MONTH("30 days"), YEAR("Year"), ALL("All time");

    fun since(now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = when (this) {
            TODAY -> today
            WEEK -> today.minusDays(6)
            MONTH -> today.minusDays(29)
            YEAR -> today.minusDays(364)
            ALL -> return 0
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(now)
    }
}

data class SongStat(
    val trackId: Long,
    val title: String,
    val artist: String,
    val language: String,
    val isFavourite: Boolean,
    val plays: Int,
    val ms: Long,
    val lastPlayed: Long,
    val firstSeen: Long,
    val topSource: String,
    /** 0–100: plays in the last 90 days, plus 20 if you hearted it. */
    val score: Int,
)

data class GroupStat(val name: String, val plays: Int, val ms: Long, val songs: Int)

object Stats {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun inRange(rows: List<PlayRow>, range: Range): List<PlayRow> {
        val since = range.since()
        return rows.filter { it.startedAt >= since }
    }

    fun songs(all: List<PlayRow>, range: Range): List<SongStat> {
        val ninetyDays = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val recentCounts = all.filter { it.startedAt >= ninetyDays }.groupingBy { it.trackId }.eachCount()
        return inRange(all, range).groupBy { it.trackId }.map { (trackId, plays) ->
            val first = plays.first()
            val recent = recentCounts[trackId] ?: 0
            SongStat(
                trackId = trackId,
                title = first.title,
                artist = first.artist,
                language = first.language,
                isFavourite = first.isFavourite,
                plays = plays.size,
                ms = plays.sumOf { it.playedMs },
                lastPlayed = plays.maxOf { it.startedAt },
                firstSeen = first.firstSeen,
                topSource = plays.groupingBy { it.sourceApp }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
                score = (recent * 10 + if (first.isFavourite) 20 else 0).coerceAtMost(100),
            )
        }
    }

    fun groups(rows: List<PlayRow>, keyOf: (PlayRow) -> String): List<GroupStat> =
        rows.groupBy(keyOf).map { (name, plays) ->
            GroupStat(name, plays.size, plays.sumOf { it.playedMs }, plays.map { it.trackId }.distinct().size)
        }.sortedByDescending { it.ms }

    fun artistOf(row: PlayRow): String = row.artist.ifBlank { "Unknown artist" }

    /** Minutes listened per day for the last [days] days, oldest first. */
    fun dailyMinutes(rows: List<PlayRow>, days: Int): List<Pair<String, Float>> {
        val today = LocalDate.now(zone)
        val byDay = rows.groupBy { day(it.startedAt) }
        val fmt = DateTimeFormatter.ofPattern(if (days <= 7) "EEE" else "d", Locale.getDefault())
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val ms = byDay[d]?.sumOf { it.playedMs } ?: 0L
            d.format(fmt) to ms / 60_000f
        }
    }

    /** Minutes listened in each hour of the day (0–23). */
    fun hourly(rows: List<PlayRow>): List<Float> {
        val out = FloatArray(24)
        rows.forEach { out[Instant.ofEpochMilli(it.startedAt).atZone(zone).hour] += it.playedMs / 60_000f }
        return out.toList()
    }

    /** Days in a row, ending today (or yesterday), with at least one play. */
    fun streak(rows: List<PlayRow>): Int {
        val days = rows.map { day(it.startedAt) }.toSet()
        var d = LocalDate.now(zone)
        if (d !in days) d = d.minusDays(1)
        var count = 0
        while (d in days) {
            count++
            d = d.minusDays(1)
        }
        return count
    }

    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h} h ${m} m"
            totalMin > 0 -> "${m} m"
            else -> "${ms / 1000} s"
        }
    }

    fun time(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    fun date(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    fun dayHeader(d: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault()))
        }
    }
}
