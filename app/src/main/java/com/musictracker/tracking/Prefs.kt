package com.musictracker.tracking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Start/Stop switch and the Label lab switch, shared by the app, the services and the tile. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_ENABLED = "listening"
    private const val KEY_LAB = "label_lab"

    private val _listening = MutableStateFlow<Boolean?>(null)
    private val _lab = MutableStateFlow<Boolean?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isListening(context: Context): Boolean =
        _listening.value ?: prefs(context).getBoolean(KEY_ENABLED, false).also { _listening.value = it }

    fun setListening(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        _listening.value = on
        if (!on) PlayTracker.endAll()
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
}
