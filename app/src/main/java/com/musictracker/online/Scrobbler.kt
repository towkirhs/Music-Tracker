package com.musictracker.online

import android.content.Context
import com.musictracker.data.AppDatabase
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Sends finished plays (30 seconds or more, with a known artist) to your Last.fm profile. Runs a minute after a song
 * ends, so the song's length is known; when offline, the plays wait for the next run.
 */
object Scrobbler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** A play ending kicks this; kicks while a run is waiting join that run. */
    @Synchronized
    fun kick(context: Context, waitMs: Long = 60_000) {
        val app = context.applicationContext
        if (!ready(app) || job?.isActive == true) return
        job = scope.launch {
            delay(waitMs)
            try {
                run(app)
            } catch (e: IOException) {
                android.util.Log.w("Scrobbler", "will try again later", e)
            }
        }
    }

    private fun ready(context: Context) = Prefs.scrobbleOn(context) &&
        Prefs.lastFmSession(context).isNotBlank() && Prefs.lastFmKey(context).isNotBlank() && Prefs.lastFmSecret(context).isNotBlank()

    private suspend fun run(context: Context) {
        val dao = AppDatabase.get(context).music()
        while (ready(context)) {
            val rows = dao.playsToScrobble(Prefs.scrobbledUntil(context), 50)
            if (rows.isEmpty()) return
            try {
                LastFmAccount.scrobble(
                    Prefs.lastFmKey(context), Prefs.lastFmSecret(context), Prefs.lastFmSession(context),
                    rows.map { LastFmAccount.Scrobble(it.title, it.artist, it.startedAt / 1000) },
                )
            } catch (e: LastFmAccount.Rejected) {
                // 9: you removed the app's access on last.fm. Other codes: Last.fm is busy, try later.
                if (e.code == 9) Prefs.setLastFmSession(context, "", "")
                return
            }
            Prefs.setScrobbledUntil(context, rows.last().startedAt)
        }
    }
}
