/*
 * Recognition request adapted from ArchiveTune's shazamkit module
 * (© Rukamori — github.com/rukamori, GPL-3.0), rewritten for OkHttp and org.json.
 */

package com.musictracker.recognize

import com.musictracker.net.Http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

/** A song Shazam named, with the links it gave. */
data class Recognized(
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
    val genre: String?,
    val released: String?,
    val label: String?,
    val shazamUrl: String?,
    val appleMusicUrl: String?,
    val spotifyUrl: String?,
    val youtubeVideoId: String?,
)

/** Names music from a few seconds of 16 kHz mono audio, the way Shazam does. Free, no account. */
object Shazam {

    sealed interface Result {
        data class Found(val song: Recognized) : Result
        data object NoMatch : Result
        data class Failed(val reason: String) : Result
    }

    private const val SAMPLE_RATE = 16_000

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
    )
    private val timezones = listOf("Europe/Paris", "Europe/London", "America/New_York", "Asia/Tokyo", "Asia/Dubai")

    fun recognize(floats: FloatArray): Result {
        val shorts = ShortArray(floats.size) { i -> (floats[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        return recognize(shorts)
    }

    /** Blocking: call off the main thread. */
    fun recognize(samples: ShortArray): Result {
        if (samples.size < SAMPLE_RATE * 3) return Result.Failed("less than 3 seconds of audio")
        val signature = ShazamSignatureGenerator().apply { feedPcm16Mono(samples) }.nextSignatureOrNull()
            ?: return Result.NoMatch
        return runCatching { send(signature) }.getOrElse { Result.Failed(it.message ?: "no internet") }
    }

    private fun send(signature: ShazamSignature): Result {
        val now = System.currentTimeMillis() / 1000
        val body = JSONObject()
            .put(
                "geolocation",
                JSONObject()
                    .put("altitude", Random.nextDouble() * 400 + 100)
                    .put("latitude", Random.nextDouble() * 180 - 90)
                    .put("longitude", Random.nextDouble() * 360 - 180),
            )
            .put(
                "signature",
                JSONObject()
                    .put("samplems", signature.sampleDurationMs)
                    .put("timestamp", now)
                    .put("uri", signature.uri),
            )
            .put("timestamp", now)
            .put("timezone", timezones.random())
        val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/" +
            UUID.randomUUID().toString().uppercase() + "/" + UUID.randomUUID() +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgents.random())
            .header("Content-Language", "en_US")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Http.client.newCall(request).execute().use { response ->
            when (response.code) {
                in 200..299 -> {}
                404 -> return Result.NoMatch
                429 -> return Result.Failed("too many tries, wait a minute")
                else -> return Result.Failed("Shazam error ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val track = json.optJSONObject("track") ?: return Result.NoMatch
            return Result.Found(parse(track))
        }
    }

    private fun parse(track: JSONObject): Recognized {
        val sections = track.optJSONArray("sections").objects()
        val songMeta = sections.firstOrNull { it.optString("type") == "SONG" }?.optJSONArray("metadata").objects()
        fun meta(title: String) = songMeta.firstOrNull { it.optString("title") == title }?.optString("text")?.ifBlank { null }

        val hub = track.optJSONObject("hub")
        val options = hub?.optJSONArray("options").objects()
        val apple = options.firstOrNull { it.optString("providername").contains("apple", true) }
            ?.optJSONArray("actions").objects().firstOrNull()?.optString("uri")
        val spotify = hub?.optJSONArray("providers").objects()
            .firstOrNull { it.optString("caption").contains("spotify", true) }
            ?.optJSONArray("actions").objects().firstOrNull()?.optString("uri")
        val videoUri = sections.firstOrNull { it.optString("type") == "VIDEO" }?.optJSONObject("youtubeurl")
            ?.optJSONArray("actions").objects().firstOrNull()?.optString("uri")
            ?: options.firstOrNull { it.optString("type").contains("video", true) }
                ?.optJSONArray("actions").objects().firstOrNull()?.optString("uri")
        val images = track.optJSONObject("images")
        return Recognized(
            title = track.optString("title"),
            artist = track.optString("subtitle"),
            album = meta("Album"),
            coverUrl = images?.optString("coverarthq")?.ifBlank { null } ?: images?.optString("coverart")?.ifBlank { null },
            genre = track.optJSONObject("genres")?.optString("primary")?.ifBlank { null },
            released = meta("Released"),
            label = meta("Label"),
            shazamUrl = track.optString("url").ifBlank { null },
            appleMusicUrl = apple?.ifBlank { null },
            spotifyUrl = spotify?.ifBlank { null },
            youtubeVideoId = videoUri?.let { YOUTUBE_ID.find(it)?.groupValues?.get(1) },
        )
    }

    private val YOUTUBE_ID = Regex("""(?:v=|youtu\.be/|/embed/)([A-Za-z0-9_-]{11})""")

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
