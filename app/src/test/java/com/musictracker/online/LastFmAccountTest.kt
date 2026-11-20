package com.musictracker.online

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastFmAccountTest {

    @Test fun signatureFollowsLastFmRules() = assertEquals(
        "c03f5c28a306bc8753452981c11fb679",
        LastFmAccount.sign(mapOf("method" to "auth.getSession", "api_key" to "abc", "token" to "tok", "format" to "json"), "sec"),
    )

    @Test fun recentTracksSkipNowPlaying() {
        val page = LastFmAccount.parseRecentTracks(
            JSONObject(
                """{"recenttracks":{"track":[
                    {"name":"Kesariya","artist":{"#text":"Arijit Singh"},"@attr":{"nowplaying":"true"}},
                    {"name":"Alo","artist":{"#text":"Tahsan"},"date":{"uts":"1700000000"}}],
                  "@attr":{"totalPages":"3"}}}""",
            ),
        )
        assertEquals(listOf(LastFmAccount.Scrobble("Alo", "Tahsan", 1700000000)), page.scrobbles)
        assertEquals(3, page.totalPages)
    }

    @Test fun singleTrackComesAsObject() {
        val page = LastFmAccount.parseRecentTracks(
            JSONObject("""{"recenttracks":{"track":{"name":"Alo","artist":{"#text":"Tahsan"},"date":{"uts":"5"}},"@attr":{"totalPages":"1"}}}"""),
        )
        assertEquals(1, page.scrobbles.size)
    }
}
