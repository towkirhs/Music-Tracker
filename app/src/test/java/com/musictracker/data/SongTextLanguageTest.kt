package com.musictracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SongTextLanguageTest {

    private fun lang(title: String, artist: String = "", hints: List<String> = emptyList(), artistLanguage: String? = null) =
        SongText.detectLanguage(title, artist, hints, artistLanguage)

    @Test fun banglaScript() = assertEquals("Bangla", lang("তুমি রবে নীরবে"))

    @Test fun hindiScript() = assertEquals("Hindi", lang("तुम ही हो"))

    @Test fun romanisedHindiTitles() {
        assertEquals("Hindi", lang("Tum Hi Ho", "Arijit Singh"))
        assertEquals("Hindi", lang("Channa Mereya"))
        assertEquals("Hindi", lang("Tera Ban Jaunga"))
        assertEquals("Hindi", lang("Dil Diyan Gallan"))
        assertEquals("Hindi", lang("Kesariya"))
    }

    @Test fun romanisedBanglaTitles() {
        assertEquals("Bangla", lang("Tumi Robe Nirobe"))
        assertEquals("Bangla", lang("Amar Bhindeshi Tara"))
        assertEquals("Bangla", lang("Bhalobashi", "Tahsan"))
        assertEquals("Bangla", lang("Ekla Cholo Re"))
        assertEquals("Bangla", lang("Tomake Chai"))
    }

    @Test fun englishStaysEnglish() {
        assertEquals("English/other", lang("Blinding Lights", "The Weeknd"))
        assertEquals("English/other", lang("Shape of You", "Ed Sheeran"))
        assertEquals("English/other", lang("Let Me Down Slowly", "Alec Benjamin"))
        assertEquals("English/other", lang("Mon Amour", "Zzoilo"))
        assertEquals("English/other", lang("Main Street"))
    }

    @Test fun onlineGenreAndTags() {
        assertEquals("Hindi", lang("Something Unknown", hints = listOf("Bollywood")))
        assertEquals("Bangla", lang("Something Unknown", hints = listOf("rock", "bengali")))
        assertEquals("Bangla", lang("Something Unknown", hints = listOf("Bangladeshi")))
    }

    @Test fun knownArtist() {
        assertEquals("Bangla", lang("Alo", "Tahsan"))
        assertEquals("Hindi", lang("Pal", "Arijit Singh"))
    }

    @Test fun artistsOtherSongs() = assertEquals("Bangla", lang("Untitled", "Some Band", artistLanguage = "Bangla"))

    @Test fun titleBeatsArtist() = assertEquals("Bangla", lang("Tumi Jake Bhalobasho", "Arijit Singh"))
}
