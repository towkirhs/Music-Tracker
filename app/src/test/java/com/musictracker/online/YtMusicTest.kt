package com.musictracker.online

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtMusicTest {

    private fun runs(vararg texts: String) = texts.joinToString(",", "[", "]") { """{"text":"$it"}""" }

    private fun song(id: String, title: String, artistRuns: String) = """
        {"musicResponsiveListItemRenderer":{
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"small"},{"url":"big-$id"}]}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title",
              "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":$artistRuns}}}],
          "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"4:05"}]}}}],
          "playlistItemData":{"videoId":"$id"}}}"""

    private val page = JSONObject(
        """{"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{"contents":[
            {"musicPlaylistShelfRenderer":{"contents":[
              ${song("a1", "Kesariya", runs("Pritam", ", ", "Arijit Singh"))},
              ${song("b2", "Alo", runs("Tahsan", " • ", "Single"))},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}
            ]}}]}}}}}""",
    )

    @Test fun songsFromAPlaylistPage() {
        val songs = YtMusic.parseSongs(page)
        assertEquals(2, songs.size)
        assertEquals("a1", songs[0].videoId)
        assertEquals("Kesariya", songs[0].title)
        assertEquals("Pritam, Arijit Singh", songs[0].artist)
        assertEquals("big-a1", songs[0].thumbnail)
        assertEquals(245_000L, songs[0].durationMs)
        assertEquals("Tahsan", songs[1].artist)
    }

    @Test fun continuationToken() = assertEquals("NEXT", YtMusic.continuationToken(page))

    @Test fun playlistsSkipTheNewPlaylistButton() {
        val json = JSONObject(
            """{"items":[
              {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"New playlist"}]},
                "navigationEndpoint":{"createPlaylistEndpoint":{}}}},
              {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Bangla mix"}]},
                "subtitle":{"runs":[{"text":"Playlist"},{"text":" • "},{"text":"20 songs"}]},
                "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"t1"},{"url":"t2"}]}}},
                "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123"}}}}]}""",
        )
        val lists = YtMusic.parsePlaylists(json)
        assertEquals(1, lists.size)
        assertEquals(YtMusic.Playlist("VLPL123", "Bangla mix", "Playlist • 20 songs", "t2"), lists.first())
    }

    @Test fun signedInNeedsSapisid() {
        assertTrue(YtMusic.isSignedIn("PREF=x; SAPISID=abc/def; SID=1"))
        assertFalse(YtMusic.isSignedIn("PREF=x; YSC=2"))
    }

    @Test fun authorizationHeader() = assertEquals(
        "SAPISIDHASH 1700000000_6690bb76bc79c5e251249629416bd361bc77a360",
        YtMusic.authorization("SAPI", 1700000000),
    )
}
