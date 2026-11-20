package com.musictracker.tracking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Start/Stop switch and the Label lab switch, shared by the app, the services and the tile. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_ENABLED = "listening"
    private const val KEY_LAB = "label_lab"
    private const val KEY_LYRICS = "lyrics"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_GENIUS = "genius_token"

    private val _listening = MutableStateFlow<Boolean?>(null)
    private val _lab = MutableStateFlow<Boolean?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isListening(context: Context): Boolean =
        _listening.value ?: prefs(context).getBoolean(KEY_ENABLED, false).also { _listening.value = it }

    fun setListening(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        _listening.value = on
        if (!on) {
            PlayTracker.endAll()
            com.musictracker.audio.AudioCaptureService.stop(context)
        }
    }

    fun listeningFlow(context: Context): StateFlow<Boolean?> {
        isListening(context)
        return _listening
    }

    fun isLabOn(context: Context): Boolean =
        _lab.value ?: prefs(context).getBoolean(KEY_LAB, false).also { _lab.value = it }

    fun setLab(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LAB, on).apply()
        _lab.value = on
    }

    fun labFlow(context: Context): StateFlow<Boolean?> {
        isLabOn(context)
        return _lab
    }

    private val _lyrics = MutableStateFlow<Boolean?>(null)

    /** Recognise unnamed songs by listening to their lyrics (Whisper). */
    fun isLyricsOn(context: Context): Boolean =
        _lyrics.value ?: prefs(context).getBoolean(KEY_LYRICS, false).also { _lyrics.value = it }

    fun setLyrics(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LYRICS, on).apply()
        _lyrics.value = on
        if (!on) com.musictracker.audio.AudioCaptureService.stop(context)
    }

    fun lyricsFlow(context: Context): StateFlow<Boolean?> {
        isLyricsOn(context)
        return _lyrics
    }

    fun isWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_WIFI_ONLY, on).apply()

    fun geniusToken(context: Context): String = prefs(context).getString(KEY_GENIUS, "").orEmpty()

    fun setGeniusToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_GENIUS, token.trim()).apply()

    private const val KEY_LASTFM_KEY = "lastfm_key"
    private const val KEY_LASTFM_USER = "lastfm_user"

    /** Free Last.fm API key for discovery, radio and tags; "" when not set. */
    fun lastFmKey(context: Context): String = prefs(context).getString(KEY_LASTFM_KEY, "").orEmpty()
    fun lastFmUser(context: Context): String = prefs(context).getString(KEY_LASTFM_USER, "").orEmpty()
    fun setLastFm(context: Context, key: String, user: String) =
        prefs(context).edit().putString(KEY_LASTFM_KEY, key.trim()).putString(KEY_LASTFM_USER, user.trim()).apply()

    private const val KEY_SHAZAM = "shazam_reels"
    private const val KEY_RECOGNIZED_TO_LIBRARY = "recognized_to_library"
    private const val KEY_COUNT_PLAYER = "count_player"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_SPEED = "player_speed"
    private const val KEY_PITCH = "player_pitch"

    /** Name unlabelled reels by their audio fingerprint (Shazam) before trying lyrics. Needs internet. */
    fun isShazamOn(context: Context): Boolean = prefs(context).getBoolean(KEY_SHAZAM, true)
    fun setShazamOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SHAZAM, on).apply()

    /** Songs named with the Recognise button also go into the library as a play. */
    fun recognizedToLibrary(context: Context): Boolean = prefs(context).getBoolean(KEY_RECOGNIZED_TO_LIBRARY, true)
    fun setRecognizedToLibrary(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_RECOGNIZED_TO_LIBRARY, on).apply()

    /** Songs played in the built-in player count in the library and stats. */
    fun countPlayer(context: Context): Boolean = prefs(context).getBoolean(KEY_COUNT_PLAYER, true)
    fun setCountPlayer(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_COUNT_PLAYER, on).apply()

    fun autoUpdateCheck(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)
    fun setAutoUpdateCheck(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, on).apply()

    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong(KEY_LAST_UPDATE_CHECK, 0L)
    fun setLastUpdateCheck(context: Context, at: Long) = prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK, at).apply()

    fun playerSpeed(context: Context): Float = prefs(context).getFloat(KEY_SPEED, 1f)
    fun playerPitch(context: Context): Float = prefs(context).getFloat(KEY_PITCH, 1f)
    fun setPlayerSpeedPitch(context: Context, speed: Float, pitch: Float) =
        prefs(context).edit().putFloat(KEY_SPEED, speed).putFloat(KEY_PITCH, pitch).apply()
}
