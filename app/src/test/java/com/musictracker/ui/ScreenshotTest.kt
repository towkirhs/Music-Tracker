package com.musictracker.ui

import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.musictracker.data.AppDatabase
import com.musictracker.data.Play
import com.musictracker.data.Track
import com.musictracker.online.LastFm
import com.musictracker.online.YtMusic
import com.musictracker.tracking.Prefs
import com.musictracker.player.MusicPlayer
import com.musictracker.player.PlayerState
import com.musictracker.player.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen with sample songs into app/build/screenshots, so the look can be checked
 * without a phone. Run: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val errors = mutableListOf<String>()

    private fun shot(name: String, action: () -> Unit = {}) {
        try {
            action()
            rule.waitForIdle()
            captureScreenRoboImage("build/screenshots/$name.png")
        } catch (e: Throwable) {
            errors += "$name: $e"
            e.printStackTrace()
        }
    }

    private fun tap(text: String) = rule.onNode(hasText(text) and hasClickAction()).performClick()

    private fun tapIcon(description: String) = rule.onNode(hasContentDescription(description)).performClick()

    @Test
    fun screens() {
        seed()
        setPlaying() // so the mini player shows above the tabs
        val openPlayer = MutableStateFlow(false)
        // A pretend YouTube Music sign-in, with its playlists already loaded, so nothing goes online.
        Prefs.setYtMusicCookie(ApplicationProvider.getApplicationContext(), "SAPISID=test")
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        flow<YtLibraryState>(vm, "_ytLibrary").value = YtLibraryState(
            loaded = true,
            playlists = listOf(
                YtMusic.Playlist("VL1", "Bangla mix", "Playlist • 24 songs", null),
                YtMusic.Playlist("VL2", "Gym", "Playlist • 40 songs", null),
            ),
        )
        rule.setContent {
            MusicTrackerTheme { MusicTrackerApp(onRequestAudio = {}, openPlayer = openPlayer, vm = vm) }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Tum Hi Ho").fetchSemanticsNodes().isNotEmpty() }

        shot("01-home")
        shot("02-listen") { tap("Listen") }
        shot("02b-listen-discover") {
            showDiscover(vm)
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Artists for you"))
        }
        shot("02c-playlist") {
            flow<PlaylistState?>(vm, "_openPlaylist").value = PlaylistState(
                "Liked songs", null,
                songs = listOf(
                    Song("a", "Kesariya", "Arijit Singh"),
                    Song("b", "Alo", "Tahsan"),
                    Song("c", "Blinding Lights", "The Weeknd"),
                ),
            )
        }
        shot("03-library") {
            flow<PlaylistState?>(vm, "_openPlaylist").value = null
            rule.waitForIdle()
            tap("Library")
        }
        shot("04-library-artists") { tap("Artists") }
        shot("05-song-sheet") {
            tap("Songs")
            rule.waitForIdle()
            rule.onAllNodesWithText("Tum Hi Ho", substring = true)[0].performClick()
        }
        shot("06-song-sheet-edit") { tap("Edit details") }
        shot("07-stats") {
            Espresso.pressBack() // close the song sheet
            rule.waitForIdle()
            tap("Stats")
        }
        shot("08-history") { tap("History") }
        shot("09-settings") { tap("Settings") }
        shot("09b-settings-accounts") {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Spotify"))
        }
        shot("10-player") {
            tap("Home")
            openPlayer.value = true
        }
        shot("11-player-lyrics") { tap("Lyrics") }
        shot("12-player-expanded") { tapIcon("Expand") }

        if (errors.isNotEmpty()) throw AssertionError(errors.joinToString("\n"))
    }

    /** One of the view model's private state flows, to show a state that would need the internet. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(vm: MainViewModel, name: String): MutableStateFlow<T> =
        MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    /** What the Listen tab shows once Last.fm suggestions have loaded (CI has no Last.fm key). */
    private fun showDiscover(vm: MainViewModel) {
        flow<DiscoverState>(vm, "_discover").value = DiscoverState(
            basedOn = LastFm.Track("Tum Hi Ho", "Arijit Singh"),
            similar = listOf(
                LastFm.Track("Sunn Raha Hai", "Ankit Tiwari"),
                LastFm.Track("Hasi", "Ami Mishra"),
                LastFm.Track("Muskurane", "Arijit Singh"),
            ),
            artists = listOf("Ankit Tiwari", "Mohit Chauhan", "Shreya Ghoshal", "Armaan Malik"),
            bangla = listOf(LastFm.Track("Bhalo Lagar Gaan", "Shironamhin"), LastFm.Track("Ekla Cholo Re", "Rabindranath Tagore")),
            hindi = listOf(LastFm.Track("Kesariya", "Arijit Singh")),
        )
    }

    private fun setPlaying() {
        val field = MusicPlayer::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(MusicPlayer) as MutableStateFlow<PlayerState>
        val queue = listOf(
            Song(null, "Tum Hi Ho", "Arijit Singh", durationMs = 262_000),
            Song(null, "Tumi Robe Nirobe", "Rabindrasangeet"),
            Song(null, "Blinding Lights", "The Weeknd"),
            Song(null, "Kesariya", "Arijit Singh"),
        )
        state.value = PlayerState(current = queue[0], isPlaying = true, durationMs = 262_000, queue = queue, index = 0)
    }

    private fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.get(app).music()
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val songs = listOf(
            Triple("Tum Hi Ho", "Arijit Singh", "Hindi"),
            Triple("Kesariya", "Arijit Singh", "Hindi"),
            Triple("Tumi Robe Nirobe", "Rabindrasangeet", "Bangla"),
            Triple("Bhalobashi", "Tahsan", "Bangla"),
            Triple("Blinding Lights", "The Weeknd", "English/other"),
            Triple("Shape of You", "Ed Sheeran", "English/other"),
            Triple("Apna Bana Le", "Arijit Singh", "Hindi"),
            Triple("Amar Shonar Bangla", "", "Bangla"),
        )
        val apps = listOf("Instagram", "YouTube", "TikTok", "Spotify")
        songs.forEachIndexed { i, (title, artist, language) ->
            val id = dao.insertTrack(
                Track(
                    title = title,
                    artist = artist,
                    normalizedKey = "$title|$artist".lowercase(),
                    language = language,
                    labelRaw = title,
                    firstSeen = now - (i + 2) * 24 * hour,
                    isFavourite = i % 3 == 0,
                    // Already looked up, so the test doesn't go online.
                    lookedUpAt = now,
                    genre = listOf("Bollywood", "Indian Pop", "Bengali", "Pop")[i % 4],
                    onlineTitle = if (i == 0) title else "",
                    onlineArtist = if (i == 0) "Arijit Singh & Mithoon" else "",
                ),
            )
            repeat(songs.size - i + 1) { n ->
                dao.insertPlay(
                    Play(
                        trackId = id,
                        startedAt = now - (n * 9 + i) * hour - i * 60_000L,
                        playedMs = 45_000L + (n * 17_000L) % 160_000L,
                        sourceApp = apps[(i + n) % apps.size],
                        detector = "label",
                    ),
                )
            }
        }
    }
}
