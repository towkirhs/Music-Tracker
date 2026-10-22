package com.musictracker.tracking

import java.util.Locale

/** One piece of on-screen text, with the id of the view it came from. */
data class ScreenText(
    val text: String,
    val viewId: String,
    val isDescription: Boolean,
    /** Where the view sits on screen, in pixels (0 if unknown). */
    val top: Int = 0,
    val bottom: Int = 0,
)

/**
 * Finds the song label in the on-screen text of Reels, Shorts and TikTok.
 *
 * Each app shows the song differently and changes it with updates, so this looks for strong
 * signals only: a view whose id mentions audio/music/sound/song, a music-note symbol, or text
 * that starts with "Audio", "Music", "Sound" or "Song". Use the Label lab in Settings to see the
 * raw text on your phone and tune the rules below.
 */
object LabelParser {

    /** Apps the label reader watches, with the name shown in the library. */
    val WATCHED = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.zhiliaoapp.musically.go" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook",
    )

    /** Whether the app writes "Artist · Song" (true) or "Song · Artist" (false). */
    private val ARTIST_FIRST = mapOf(
        "Instagram" to true,
        "Facebook" to true,
        "YouTube" to false,
        "TikTok" to false,
    )

    sealed interface Result {
        data class Song(val title: String, val artist: String, val raw: String) : Result
        data class OriginalAudio(val raw: String) : Result
        data object None : Result
    }

    private val ID_WORDS = listOf("audio", "music", "sound", "song", "track")
    private val ID_SKIP = listOf("mute", "volume", "toggle", "record", "camera", "use_", "button_container")
    private val NOTES = listOf('\u266A', '\u266B', '\u266C', '\uD83C')
    private val PREFIXES = listOf(
        "original audio", "original sound", "audio", "music", "sound", "song", "গান", "अडियो",
    )
    private val ORIGINAL = listOf(
        "original audio", "original sound", "originalton", "suara asli",
        "অরিজিনাল অডিও", // অরিজিনাল অডিও
        "আসল অডিও", // আসল অডিও
        "मूल ऑडियो", // मूल ऑडियो
    )
    private val REJECT_ENDINGS = listOf("likes", "views", "followers", "comments", "shares", "ago", "muted", "unmuted")
    private val SEPARATORS = listOf(" · ", " • ", " - ", " – ", " — ", " | ")
    private val leadingJunk = Regex("""^[\s\p{So}\p{Cs}:,\-]+""")
    private val trailingJunk = Regex("""[\s,.]*(button|double tap.*|tap to.*)$""", RegexOption.IGNORE_CASE)

    fun parse(appName: String, texts: List<ScreenText>, screenHeight: Int = 0): Result {
        if (appName == "Instagram") {
            val result = instagram(texts, screenHeight)
            if (result != Result.None) return result
        }
        var original: Result.OriginalAudio? = null
        for (candidate in candidates(texts)) {
            val cleaned = clean(candidate) ?: continue
            if (isOriginal(cleaned)) {
                if (original == null) original = Result.OriginalAudio(cleaned)
                continue
            }
            split(appName, cleaned)?.let { return it }
        }
        return original ?: Result.None
    }


    private const val DOT = " \u00B7 "
    private val mayInclude = Regex("""may include audio from (.+?)\)?\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Instagram (seen on the Label lab, Oct 2026):
     * - Reels: an unlabelled description "Artist, Artist · Song title" beside the reel.
     * - Feed videos: view id "secondary_label" with the same text.
     * - Creator audio: "username · Original audio", sometimes "(may include audio from Song)".
     * Several reels can be on screen while scrolling, so the label inside the reel that
     * fills most of the screen wins; captions end with "…" and are skipped.
     */
    private fun instagram(texts: List<ScreenText>, screenHeight: Int): Result {
        val labels = texts.filter { t ->
            val s = t.text.trim()
            val id = t.viewId.substringAfter(":id/")
            s.contains(DOT) && s.length in 4..220 &&
                !s.endsWith("\u2026") && !s.endsWith("...") && !s.contains('\n') &&
                (id.isEmpty() || id == "secondary_label") &&
                REJECT_ENDINGS.none { s.lowercase(Locale.ROOT).endsWith(it) }
        }.distinctBy { it.text.trim() }
        if (labels.isEmpty()) return Result.None

        val reels = texts.filter { it.viewId.endsWith("clips_video_container") && it.bottom > it.top }
        val main = reels.maxByOrNull { it.bottom - it.top }
        val inMain = if (main != null) labels.filter { it.top >= main.top && it.bottom <= main.bottom } else labels
        val pool = inMain.ifEmpty { labels }
        val middle = if (screenHeight > 0) screenHeight / 2 else 0
        val pick = pool.minByOrNull { kotlin.math.abs((it.top + it.bottom) / 2 - middle) } ?: return Result.None

        val raw = pick.text.trim()
        mayInclude.find(raw)?.let { m ->
            val song = m.groupValues[1].trim().trimEnd(')').trim()
            if (song.isNotEmpty()) return Result.Song(song, "", raw)
        }
        if (isOriginal(raw)) return Result.OriginalAudio(raw)
        val i = raw.indexOf(DOT)
        val artist = raw.substring(0, i).trim()
        val title = raw.substring(i + DOT.length).trim()
        if (title.isEmpty()) return Result.None
        return Result.Song(title, artist, raw)
    }

    /**
     * An id for the reel on screen when it has no song name, so lyrics recognition runs once per reel.
     * Instagram: the reel filling the screen ("Reel by …") plus its like count, which differs per reel.
     * Other apps: the "original sound - creator" label.
     */
    fun unknownReelId(appName: String, texts: List<ScreenText>, result: Result): String? {
        if (appName == "Instagram") {
            val main = texts.filter { it.viewId.endsWith("clips_video_container") && it.bottom > it.top }
                .maxByOrNull { it.bottom - it.top }
            if (main != null) {
                val likes = texts.firstOrNull {
                    it.viewId.endsWith("like_count") && it.top >= main.top && it.bottom <= main.bottom
                }?.text.orEmpty()
                return "ig|" + main.text + "|" + likes
            }
        }
        return (result as? Result.OriginalAudio)?.let { appName + "|" + it.raw }
    }

    /** Strong candidates first: matching view ids, then music-note text, then keyword text. */
    private fun candidates(texts: List<ScreenText>): List<String> {
        val byId = mutableListOf<String>()
        val byNote = mutableListOf<String>()
        val byWord = mutableListOf<String>()
        for (t in texts) {
            val s = t.text.trim()
            if (s.length < 2 || s.length > 160 || s.contains("http")) continue
            val id = t.viewId.lowercase(Locale.ROOT)
            val lower = s.lowercase(Locale.ROOT)
            when {
                ID_WORDS.any { id.contains(it) } && ID_SKIP.none { id.contains(it) } -> byId += s
                s.first() in NOTES -> byNote += s
                PREFIXES.any { lower.startsWith(it) } -> byWord += s
            }
        }
        return byId + byNote + byWord
    }

    private fun clean(raw: String): String? {
        var s = dedupeMarquee(raw.trim())
        s = leadingJunk.replace(s, "")
        val lower = s.lowercase(Locale.ROOT)
        for (prefix in listOf("audio", "music", "sound", "song")) {
            if (lower.startsWith("$prefix:") || lower.startsWith("$prefix ,") || lower.startsWith("$prefix,")) {
                s = s.substring(prefix.length).trimStart(':', ',', ' ')
                break
            }
        }
        s = trailingJunk.replace(s, "").trim()
        val l = s.lowercase(Locale.ROOT)
        if (s.length < 2) return null
        if (REJECT_ENDINGS.any { l.endsWith(it) }) return null
        if (l in listOf("audio", "music", "sound", "song", "sounds", "remix", "use audio", "use sound")) return null
        if (s.all { it.isDigit() || it.isWhitespace() || it in ".,KkMm" }) return null
        return s
    }

    private fun isOriginal(s: String): Boolean {
        val l = s.lowercase(Locale.ROOT)
        return ORIGINAL.any { l.contains(it) }
    }

    /** TikTok scrolls the sound name and repeats it: "Song - Artist   Song - Artist". */
    private fun dedupeMarquee(s: String): String {
        val parts = s.split(Regex("""\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 2 && parts.all { it == parts[0] }) parts[0] else s
    }

    private fun split(appName: String, s: String): Result.Song? {
        val artistFirst = ARTIST_FIRST[appName] ?: false
        for (sep in SEPARATORS) {
            val i = s.indexOf(sep)
            if (i <= 0) continue
            val a = s.substring(0, i).trim()
            val b = s.substring(i + sep.length).trim()
            if (a.isEmpty() || b.isEmpty()) continue
            return if (artistFirst) Result.Song(b, a, s) else Result.Song(a, b, s)
        }
        return Result.Song(s, "", s)
    }
}
