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

    /** Why recognition can't run right now, or null if it can. */
    fun whyNot(context: Context): String? = when {
        !Prefs.isLyricsOn(context) -> "lyrics recognition is off"
        !ModelManager.isReady(context) -> "speech model not downloaded yet"
        !AudioCaptureService.running.value -> "audio not allowed right now (tap Allow audio)"
        !AudioBuffer.isFlowing() -> "audio permission is on but no sound is arriving"
        else -> null
    }

    private fun level(samples: FloatArray) = "%.4f".format(rms(samples))

    /** Transcribes the last 15 seconds right now and logs what it heard (Settings → Test now). */
    suspend fun testNow(context: Context) = mutex.withLock {
        withContext(Dispatchers.Default) {
            whyNot(context)?.let { LyricsLog.add("Test: can't run, $it"); return@withContext }
            // You switched to this app to tap Test, so skip the quiet seconds at the end.
            val samples = lastSounding(AudioBuffer.last(30), CLIP_SECONDS)
            LyricsLog.add("Test: ${samples.size / AudioBuffer.SAMPLE_RATE} s of audio, loudness ${level(samples)}")
            if (samples.size < AudioBuffer.SAMPLE_RATE * 3 || rms(samples) < 0.004f) {
                LyricsLog.add("Test: audio is silent. Play a reel with sound, or that app may block audio capture")
                return@withContext
            }
            val start = System.currentTimeMillis()
            if (!Whisper.ensureLoaded(ModelManager.file(context).path)) {
                LyricsLog.add("Test: Whisper failed to load the model (delete and re-download it)")
                return@withContext
            }
            val result = Whisper.transcribe(samples)
            val secs = (System.currentTimeMillis() - start) / 1000.0
            if (result == null) LyricsLog.add("Test: Whisper failed after %.1f s".format(secs))
            else LyricsLog.add("Test (%.1f s, %s): \u201C%s\u201D".format(secs, result.first, result.second.take(200)))
        }
    }

    /**
     * [seconds]: how much audio to use (only this reel's, since it started).
     * [lastTry]: the last try for this reel; only then is an unnamed song saved to the inbox.
     */
    suspend fun recognize(
        context: Context,
        appName: String,
        reelLabel: String,
        seconds: Int,
        lastTry: Boolean,
    ): Outcome = mutex.withLock {
        withContext(Dispatchers.Default) {
            whyNot(context)?.let {
                LyricsLog.add("$appName: can't listen, $it")
                return@withContext Outcome.NotReady
            }
            val samples = AudioBuffer.last(seconds)
            if (samples.size < AudioBuffer.SAMPLE_RATE * 4) {
                LyricsLog.add("$appName: only ${samples.size / AudioBuffer.SAMPLE_RATE} s of audio so far, will retry")
                return@withContext Outcome.NotReady
            }
            if (rms(samples) < 0.004f) {
                LyricsLog.add("$appName: audio is silent (loudness ${level(samples)}). The app may block audio capture")
                return@withContext Outcome.NoMatch
            }
            val start = System.currentTimeMillis()
            if (!Whisper.ensureLoaded(ModelManager.file(context).path)) {
                LyricsLog.add("$appName: Whisper failed to load the model")
                return@withContext Outcome.NotReady
            }
            val transcribed = Whisper.transcribe(samples)
            val secs = (System.currentTimeMillis() - start) / 1000.0
            if (transcribed == null) {
                LyricsLog.add("$appName: Whisper failed")
                return@withContext Outcome.NoMatch
            }
            val (language, raw) = transcribed
            val lyrics = clean(raw)
            LyricsLog.add("$appName: heard in %.1f s (%s): \u201C%s\u201D".format(secs, language, lyrics.take(160)))
            if (lyrics.split(' ').count { it.length > 1 } < 4) {
                LyricsLog.add(if (lastTry) "$appName: too few words to search" else "$appName: too few words yet, trying again at 16 s")
                return@withContext Outcome.NoMatch
            }

            val titles = AppDatabase.get(context).music().allTitles().map { it.title to it.artist }
            TitleMatcher.best(lyrics, titles)?.let { m ->
                LyricsLog.add("$appName: matched a title in your library: ${m.title}")
                return@withContext Outcome.Found(Heard(m.title, m.artist, "lyrics: " + lyrics.take(150), appName, "lyrics"))
            }

            var guessTitle = ""
            var guessArtist = ""
            val token = Prefs.geniusToken(context)
            if (token.isNotBlank()) {
                val guess = runCatching { Genius.identify(token, lyrics) }.getOrNull()
                LyricsLog.add(
                    if (guess == null) "$appName: Genius found nothing"
                    else "$appName: Genius suggests ${guess.hit.title} (${if (guess.confident) "confident" else "unsure"})",
                )
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
            if (!lastTry) {
                LyricsLog.add("$appName: no match yet, trying again at 16 s with more audio")
                return@withContext Outcome.NoMatch
            }
            LyricsLog.add("$appName: not named, saved to History \u2192 Unidentified")
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

    /** The [seconds] of audio before the last moment that had sound. */
    private fun lastSounding(all: FloatArray, seconds: Int): FloatArray {
        var end = all.size
        while (end > 0 && kotlin.math.abs(all[end - 1]) < 0.01f) end--
        val start = (end - seconds * AudioBuffer.SAMPLE_RATE).coerceAtLeast(0)
        return all.copyOfRange(start, end)
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }
}
