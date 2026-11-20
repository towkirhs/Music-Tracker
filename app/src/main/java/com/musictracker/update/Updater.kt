package com.musictracker.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.musictracker.BuildConfig
import com.musictracker.net.Http
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * The Update button: finds the newest release of this app on GitHub, downloads its APK and
 * hands it to Android's installer. Same signing key, so it installs over this version and
 * keeps the library.
 */
object Updater {

    const val REPO = "towkirhs/Music-Tracker"
    private const val DAY_MS = 24 * 3_600_000L

    data class Release(val build: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val build: Int) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val percent: Int) : State
        data object Installing : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** This app's build number: the GitHub build that made it (0 for a local build). */
    val currentBuild: Int get() = BuildConfig.VERSION_CODE - 10

    /** Checks at most once a day when the app opens, if automatic checks are on. */
    suspend fun checkIfDue(context: Context) {
        if (!Prefs.autoUpdateCheck(context)) return
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck(context) < DAY_MS) return
        check(context, quiet = true)
    }

    suspend fun check(context: Context, quiet: Boolean = false) {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        if (!quiet) _state.value = State.Checking
        val result = withContext(Dispatchers.IO) { runCatching { latest() } }
        Prefs.setLastUpdateCheck(context, System.currentTimeMillis())
        _state.value = result.fold(
            onSuccess = { r ->
                when {
                    r == null -> if (quiet) State.Idle else State.Failed("No release with an APK found on GitHub")
                    r.build > currentBuild -> State.Available(r)
                    else -> if (quiet) State.Idle else State.UpToDate(currentBuild)
                }
            },
            onFailure = { if (quiet) State.Idle else State.Failed("Couldn't reach GitHub: ${it.message ?: "no internet"}") },
        )
    }

    private fun latest(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MusicTracker-updater")
            .build()
        val json = Http.client.newCall(request).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) error("GitHub error ${r.code}")
            JSONObject(r.body?.string().orEmpty())
        }
        val tag = json.optString("tag_name")
        val build = Regex("""(\d+)\s*$""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return Release(
            build = build,
            name = json.optString("name").ifBlank { tag },
            notes = json.optString("body").trim(),
            apkUrl = apk.optString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android's "Install unknown apps" switch for Music Tracker. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads the APK straight into an install session, then asks you to confirm the install. */
    suspend fun downloadAndInstall(context: Context, release: Release) {
        if (_state.value is State.Downloading) return
        _state.value = State.Downloading(release, 0)
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(app.packageName) }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val request = Request.Builder().url(release.apkUrl).header("User-Agent", "MusicTracker-updater").build()
                    Http.client.newCall(request).execute().use { r ->
                        if (!r.isSuccessful) error("download failed (${r.code})")
                        val body = r.body ?: error("empty download")
                        val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                        session.openWrite("MusicTracker.apk", 0, total.takeIf { it > 0 } ?: -1).use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                var lastPercent = -1
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val p = (done * 100 / total).toInt()
                                        if (p != lastPercent) {
                                            lastPercent = p
                                            _state.value = State.Downloading(release, p)
                                        }
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    }
                    val callback = PendingIntent.getBroadcast(
                        app, sessionId,
                        Intent(app, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
            }
        }
        _state.value = result.fold(
            onSuccess = { State.Installing },
            onFailure = { State.Failed("Update failed: ${it.message}") },
        )
    }

    internal fun onInstallFinished(ok: Boolean, message: String?) {
        if (!ok) _state.value = State.Failed("Not installed" + (message?.let { ": $it" } ?: ""))
        else _state.value = State.Idle
    }
}

/** Gets Android's answer to the install: shows the confirm screen, or the reason it failed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.onInstallFinished(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.onInstallFinished(false, "you cancelled it")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Updater.onInstallFinished(false, msg)
                Toast.makeText(context, "Update not installed: ${msg ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
