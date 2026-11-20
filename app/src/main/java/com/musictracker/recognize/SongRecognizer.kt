package com.musictracker.recognize

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.musictracker.audio.AudioBuffer
import com.musictracker.audio.AudioCaptureService
import com.musictracker.data.AppDatabase
import com.musictracker.data.Recognition
import com.musictracker.data.Repo
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The Recognise button: listens for up to 12 seconds and names the song, like Shazam.
 * Hears either the room (microphone) or what this phone is playing (when audio capture is allowed).
 * Tries after 4, 8 and 12 seconds, so a clear song is usually named within a few seconds.
 */
object SongRecognizer {

    enum class Input(val label: String) {
        AUTO("Automatic"),
        MIC("Microphone"),
        PHONE("This phone"),
    }

    sealed interface State {
        data object Idle : State
        data class Listening(val heardFrom: String, val progress: Float, val level: Float) : State
        data class Found(val recognition: Recognition) : State
        data class NotFound(val message: String) : State
    }

    private const val RATE = AudioBuffer.SAMPLE_RATE
    private val CHECKPOINTS = listOf(4, 8, 12)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun hasMicPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Whether "This phone" can be used: audio capture is on and sound is arriving. */
    fun phoneAudioAvailable(): Boolean = AudioCaptureService.running.value && AudioBuffer.isFlowing()

    fun start(context: Context, input: Input) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val usePhone = when (input) {
            Input.PHONE -> true
            Input.MIC -> false
            Input.AUTO -> phoneAudioAvailable() && rms(AudioBuffer.last(4)) > 0.004f
        }
        job = scope.launch {
            val result = if (usePhone) fromPhone(app) else fromMic(app)
            _state.value = result
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    fun reset() {
        if (job?.isActive != true) _state.value = State.Idle
    }

    private suspend fun fromPhone(context: Context): State {
        val from = Input.PHONE.label
        if (!phoneAudioAvailable()) {
            return State.NotFound("This phone's audio isn't available. Turn on Allow audio on Home, or use the microphone.")
        }
        var lastProblem: String? = null
        for ((i, wait) in listOf(0, 4, 4).withIndex()) {
            repeat(wait * 4) {
                delay(250)
                val recent = AudioBuffer.last(1)
                _state.value = State.Listening(from, (i * 4 + it / 4f) / 12f, level(recent))
            }
            val samples = AudioBuffer.last(12)
            _state.value = State.Listening(from, (i + 1) / 3f, level(AudioBuffer.last(1)))
            if (rms(samples) < 0.004f) {
                lastProblem = "No sound is playing on this phone."
                continue
            }
            when (val r = withContext(Dispatchers.IO) { Shazam.recognize(samples) }) {
                is Shazam.Result.Found -> return save(context, r.song, from)
                is Shazam.Result.Failed -> return State.NotFound("Couldn't search: ${r.reason}.")
                Shazam.Result.NoMatch -> lastProblem = null
            }
        }
        return State.NotFound(lastProblem ?: "No match. Try again while the song is playing more clearly.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun fromMic(context: Context): State {
        val from = Input.MIC.label
        if (!hasMicPermission(context)) return State.NotFound("Allow the microphone to recognise songs around you.")
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return State.NotFound("The microphone is busy in another app.")
        }
        val total = ShortArray(RATE * CHECKPOINTS.last())
        val written = java.util.concurrent.atomic.AtomicInteger(0)
        var level = 0f
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            record.startRecording()
            // Recording runs on its own so no audio is lost while a search is in flight.
            val reader = scope.launch(Dispatchers.IO) {
                val chunk = ShortArray(RATE / 10)
                while (isActive && written.get() < total.size) {
                    val n = record.read(chunk, 0, minOf(chunk.size, total.size - written.get()))
                    if (n < 0) { failed.set(true); break }
                    if (n == 0) continue
                    chunk.copyInto(total, written.get(), 0, n)
                    written.addAndGet(n)
                    level = levelShorts(chunk, n)
                }
            }
            try {
                for (checkpoint in CHECKPOINTS) {
                    while (written.get() < checkpoint * RATE && !failed.get() && reader.isActive) {
                        _state.value = State.Listening(from, written.get().toFloat() / total.size, level)
                        delay(100)
                    }
                    if (failed.get()) return State.NotFound("The microphone stopped working.")
                    val samples = total.copyOf(written.get())
                    when (val r = withContext(Dispatchers.IO) { Shazam.recognize(samples) }) {
                        is Shazam.Result.Found -> return save(context, r.song, from)
                        is Shazam.Result.Failed -> if (checkpoint == CHECKPOINTS.last()) {
                            return State.NotFound("Couldn't search: ${r.reason}.")
                        }
                        Shazam.Result.NoMatch -> {}
                    }
                }
            } finally {
                reader.cancel()
                withContext(kotlinx.coroutines.NonCancellable) { reader.join() }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        return State.NotFound("No match. Hold the phone closer to the music and try again.")
    }

    private suspend fun save(context: Context, song: Recognized, from: String): State {
        val item = Recognition(
            recognizedAt = System.currentTimeMillis(),
            title = song.title,
            artist = song.artist,
            album = song.album.orEmpty(),
            coverUrl = song.coverUrl.orEmpty(),
            heardFrom = from,
            shazamUrl = song.shazamUrl.orEmpty(),
            spotifyUrl = song.spotifyUrl.orEmpty(),
            appleMusicUrl = song.appleMusicUrl.orEmpty(),
            youtubeVideoId = song.youtubeVideoId.orEmpty(),
        )
        val id = AppDatabase.get(context).music().insertRecognition(item)
        if (Prefs.recognizedToLibrary(context)) {
            Repo.addPlay(
                context, song.title, song.artist, "recognised: ${song.title} - ${song.artist}",
                "Recognised ($from)", "shazam", item.recognizedAt, 15_000L,
            )
        }
        return State.Found(item.copy(id = id))
    }

    private fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }

    private fun level(samples: FloatArray): Float = toLevel(rms(samples).toDouble())

    private fun levelShorts(samples: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = samples[i] / 32768.0
            sum += v * v
        }
        return toLevel(sqrt(sum / n))
    }

    /** Loudness as 0..1 for the animation (-60 dB silent, -10 dB loud). */
    private fun toLevel(rms: Double): Float {
        if (rms <= 0.0) return 0f
        return ((20 * log10(rms) + 60) / 50).toFloat().coerceIn(0f, 1f)
    }
}
