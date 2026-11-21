package com.musictracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceMusicTest {
    @Test fun taggedFileKeepsItsTags() =
        assertEquals("Kesariya" to "Arijit Singh", DeviceMusic.names("Kesariya", "Arijit Singh", "x.mp3"))

    @Test fun downloadWithoutTagsUsesFileName() =
        assertEquals("Tumi Robe Nirobe" to "Rabindrasangeet", DeviceMusic.names("Rabindrasangeet - Tumi Robe Nirobe", "<unknown>", "Rabindrasangeet - Tumi Robe Nirobe.m4a"))

    @Test fun noTitleTagUsesFileName() =
        assertEquals("Alo" to "Tahsan", DeviceMusic.names("", "", "Tahsan - Alo.m4a"))

    @Test fun plainFileName() = assertEquals("Voice of rain" to "", DeviceMusic.names("", "<unknown>", "Voice of rain.mp3"))
}
