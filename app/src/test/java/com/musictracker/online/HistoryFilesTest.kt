package com.musictracker.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryFilesTest {

    @Test fun spotifyAccountData() {
        val entries = HistoryFiles.parse(
            """[{"endTime":"2025-03-01 18:04","artistName":"Arijit Singh","trackName":"Kesariya","msPlayed":240000},
               {"endTime":"2025-03-01 18:05","artistName":"Tahsan","trackName":"Alo","msPlayed":4000}]""",
        )!!
        assertEquals(1, entries.size) // under 10 seconds is left out
        val e = entries.first()
        assertEquals("Kesariya", e.title)
        assertEquals("Arijit Singh", e.artist)
        assertEquals(Instant.parse("2025-03-01T18:00:00Z").toEpochMilli(), e.startedAt)
        assertEquals("Spotify", e.source)
    }

    @Test fun spotifyExtendedHistorySkipsPodcasts() {
        val entries = HistoryFiles.parse(
            """[{"ts":"2024-01-01T12:03:00Z","ms_played":180000,"master_metadata_track_name":"Tum Hi Ho",
                 "master_metadata_album_artist_name":"Arijit Singh","episode_name":null},
               {"ts":"2024-01-01T13:00:00Z","ms_played":900000,"master_metadata_track_name":null,
                 "master_metadata_album_artist_name":null,"episode_name":"A podcast"}]""",
        )!!
        assertEquals(1, entries.size)
        assertEquals(Instant.parse("2024-01-01T12:00:00Z").toEpochMilli(), entries.first().startedAt)
        assertEquals(180000L, entries.first().playedMs)
    }

    @Test fun takeoutKeepsOnlyYouTubeMusic() {
        val entries = HistoryFiles.parse(
            """[{"header":"YouTube Music","title":"Watched Tumi Robe Nirobe","titleUrl":"https://music.youtube.com/watch?v=x",
                 "subtitles":[{"name":"Rabindrasangeet - Topic"}],"time":"2024-05-01T10:00:00.5Z"},
               {"header":"YouTube","title":"Watched a cat video","titleUrl":"https://www.youtube.com/watch?v=y",
                 "time":"2024-05-01T11:00:00Z"}]""",
        )!!
        assertEquals(1, entries.size)
        assertEquals("Tumi Robe Nirobe", entries.first().title)
        assertEquals("Rabindrasangeet", entries.first().artist)
        assertEquals("YouTube Music", entries.first().source)
    }

    @Test fun otherJsonIsNotHistory() = assertNull(HistoryFiles.parse("""[{"foo":1}]"""))

    @Test fun zipEntryNames() {
        assertTrue(HistoryFiles.isHistoryFileName("Spotify Account Data/StreamingHistory_music_0.json"))
        assertTrue(HistoryFiles.isHistoryFileName("Spotify Extended Streaming History/Streaming_History_Audio_2023_4.json"))
        assertTrue(HistoryFiles.isHistoryFileName("Takeout/YouTube and YouTube Music/history/watch-history.json"))
        assertFalse(HistoryFiles.isHistoryFileName("Spotify Account Data/Userdata.json"))
    }
}
