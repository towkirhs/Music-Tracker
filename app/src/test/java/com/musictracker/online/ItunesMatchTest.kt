package com.musictracker.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItunesMatchTest {
    @Test fun sameTitleWithFilmNameMatches() =
        assertTrue(Itunes.sameSong("Kesariya", "Arijit Singh", "Kesariya (From \"Brahmastra\")", "Pritam, Arijit Singh & Amitabh Bhattacharya"))

    @Test fun unknownArtistMatchesOnTitle() = assertTrue(Itunes.sameSong("Tum Hi Ho", "", "Tum Hi Ho", "Arijit Singh"))

    @Test fun differentArtistDoesNotMatch() = assertFalse(Itunes.sameSong("Hello", "Adele", "Hello", "Lionel Richie"))

    @Test fun differentTitleDoesNotMatch() = assertFalse(Itunes.sameSong("Tum Hi Ho", "Arijit Singh", "Tum Se Hi", "Mohit Chauhan"))
}
