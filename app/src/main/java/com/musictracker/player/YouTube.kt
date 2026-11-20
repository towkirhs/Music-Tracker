package com.musictracker.player

import com.musictracker.net.Http
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** A song the player can play. [videoId] is filled in on first play when it came from your library. */
data class Song(
    val videoId: String?,
    val title: String,
    val artist: String,
    val thumbnail: String? = null,
    val durationMs: Long = 0,
) {
    /** What to search for when there is no video yet. */
    val query: String get() = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * YouTube Music search and audio streams through NewPipe Extractor:
 * free, no Google account, no API key, no ads. Blocking calls: use a background thread.
 */
object YouTube {

    @Volatile private var ready = false

    private fun init() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(OkHttpDownloader)
                ready = true
            }
        }
    }

    /** Songs on YouTube Music matching [query] (falls back to all YouTube videos). */
    fun search(query: String): List<Song> {
        init()
        val music = runCatching { searchWith(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }.getOrDefault(emptyList())
        if (music.isNotEmpty()) return music
        return searchWith(query, YoutubeSearchQueryHandlerFactory.VIDEOS)
    }

    private fun searchWith(query: String, filter: String): List<Song> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            val id = videoId(item.url) ?: return@mapNotNull null
            Song(
                videoId = id,
                title = item.name,
                artist = item.uploaderName.orEmpty().removeSuffix(" - Topic"),
                thumbnail = item.thumbnails.maxByOrNull { it.width }?.url,
                durationMs = item.duration.coerceAtLeast(0) * 1000,
            )
        }
    }

    private class Resolved(val url: String, val at: Long)

    private val streamCache = ConcurrentHashMap<String, Resolved>()
    private val searchCache = ConcurrentHashMap<String, String>()

    /** The video for a library song: the best match for "title artist". */
    fun videoFor(query: String): String? {
        searchCache[query]?.let { return it }
        val id = search(query).firstOrNull()?.videoId ?: return null
        searchCache[query] = id
        return id
    }

    /** A playable audio URL for [videoId], the highest-quality plain audio stream. Cached for 4 hours. */
    fun audioUrl(videoId: String): String {
        streamCache[videoId]?.takeIf { System.currentTimeMillis() - it.at < 4 * 3_600_000L }?.let { return it.url }
        init()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val stream = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull(::rank)
            ?: error("no audio stream for this song")
        streamCache[videoId] = Resolved(stream.content, System.currentTimeMillis())
        return stream.content
    }

    /** Drops a cached stream address (they expire), so the next play looks it up again. */
    fun forget(videoId: String?, query: String?) {
        (videoId?.ifBlank { null } ?: query?.let { searchCache[it] })?.let { streamCache.remove(it) }
    }

    /** Prefer higher bitrate; on a tie prefer Opus/WebM, which every phone plays. */
    private fun rank(s: AudioStream): Int = s.averageBitrate * 10 + if (s.format?.getMimeType() == "audio/webm") 1 else 0

    private val ID = Regex("""(?:v=|youtu\.be/|/shorts/)([A-Za-z0-9_-]{11})""")

    fun videoId(url: String): String? = ID.find(url)?.groupValues?.get(1)

    private object OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
                ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
            val builder = Builder()
                .url(request.url())
                .method(request.httpMethod(), body)
                .header("User-Agent", Http.USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            Http.client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("YouTube asked for a captcha", request.url())
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        }
    }
}
