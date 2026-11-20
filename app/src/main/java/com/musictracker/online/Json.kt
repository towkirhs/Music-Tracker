package com.musictracker.online

import com.musictracker.net.Http
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** GETs [url] and parses the reply as JSON. Throws IOException when offline or the server fails. */
internal fun getJson(url: HttpUrl): JSONObject {
    val request = Request.Builder().url(url).header("User-Agent", "MusicTracker/1.0").build()
    Http.client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) throw IOException("empty reply (${response.code})")
        // A busy server can answer with an HTML error page; treat that like being offline.
        return try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw IOException("not JSON (${response.code})", e)
        }
    }
}

/** A list in JSON that may come as an array, a single object (Last.fm does this for one item) or nothing. */
internal fun JSONObject.objects(name: String): List<JSONObject> {
    optJSONArray(name)?.let { arr -> return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
    return listOfNotNull(optJSONObject(name))
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
