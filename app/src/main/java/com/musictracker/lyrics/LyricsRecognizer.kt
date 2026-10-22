package com.musictracker.lyrics

import android.content.Context
import com.musictracker.audio.AudioBuffer
import com.musictracker.audio.AudioCaptureService
import com.musictracker.data.AppDatabase
import com.musictracker.data.Repo
import com.musictracker.data.Unidentified
import com.musictracker.tracking.Heard
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Names a song that has no label on screen (covers, "Original audio") from its lyrics:
 * 1. Whisper turns the last 15 seconds of audio into text, on the phone.
 * 2. The text is matched against song titles already in your library (offline).
 * 3. If that fails and you added a Genius token, Genius is searched with a few lyric words.
 * 4. Anything still unknown goes to History → Unidentified with the lyrics and any guess.
 */
object LyricsRecognizer {

    sealed interface Outcome {
        data class Found(val heard: Heard) : Outcome
        data object NoMatch : Outcome
        data object NotReady : Outcome
    }

    private const val CLIP_SECONDS = 15
    private val mutex = Mutex()

    /** Lines Whisper tends to invent over instrumental music. */
    private val junkLines = setOf("thank you", "music", "applause", "you", "bye")
    private val junkPhrases = listOf("thanks for watching", "subscribe", "amara.org", "subtitles by")

    fun canRun(context: Context): Boolean =
        Prefs.isLyricsOn(context) && ModelManager.isReady(context) &&
            AudioCaptureService.running.value && AudioBuffer.isFlowing()

    suspend fun recognize(context: Context, appName: String, reelLabel: String): Outcome = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (!canRun(context)) return@withContext Outcome.NotReady
            val samples = AudioBuffer.last(CLIP_SECONDS)
            if (samples.size < AudioBuffer.SAMPLE_RATE * 8) return@withContext Outcome.NotReady
            if (rms(samples) < 0.004f) return@withContext Outcome.NoMatch
            if (!Whisper.ensureLoaded(ModelManager.file(context).path)) return@withContext Outcome.NotReady
            val (language, raw) = Whisper.transcribe(samples) ?: return@withContext Outcome.NoMatch
            val lyrics = clean(raw)
            if (lyrics.split(' ').count { it.length > 1 } < 4) return@withContext Outcome.NoMatch

            val titles = AppDatabase.get(context).music().allTitles().map { it.title to it.artist }
            TitleMatcher.best(lyrics, titles)?.let { m ->
                return@withContext Outcome.Found(Heard(m.title, m.artist, "lyrics: " + lyrics.take(150), appName, "lyrics"))
            }

            var guessTitle = ""
            var guessArtist = ""
            val token = Prefs.geniusToken(context)
            if (token.isNotBlank()) {
                val guess = runCatching { Genius.identify(token, lyrics) }.getOrNull()
                if (guess != null && guess.confident) {
                    return@withContext Outcome.Found(
                        Heard(guess.hit.title, guess.hit.artist, "lyrics: " + lyrics.take(150), appName, "lyrics+genius"),
                    )
                }
                if (guess != null) {
                    guessTitle = guess.hit.title
                    guessArtist = guess.hit.artist
                }
            }
            Repo.addUnidentified(
                context,
                Unidentified(
                    capturedAt = System.currentTimeMillis(),
                    sourceApp = appName,
                    reelLabel = reelLabel,
                    lyrics = lyrics.take(600),
                    language = language,
                    guessTitle = guessTitle,
                    guessArtist = guessArtist,
                ),
            )
            Outcome.NoMatch
        }
    }

    private fun clean(text: String): String {
        val noTags = text.replace(Regex("""\[[^\]]*]|\([^)]*\)|[♪♫*]"""), " ")
        val lines = noTags.split(Regex("""[.!?\n]""")).map { it.trim() }.filter { line ->
            val l = line.lowercase()
            line.isNotEmpty() && l !in junkLines && junkPhrases.none { l.contains(it) }
        }
        return lines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }
}
