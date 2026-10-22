package com.musictracker.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the Whisper speech model once (about 57 MB, free from Hugging Face) into the app's
 * own storage. App updates keep it; only uninstalling or clearing app data removes it.
 */
object ModelManager {
    const val MODEL_NAME = "ggml-base-q5_1.bin"
    private const val URL_MODEL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$MODEL_NAME"
    private const val MIN_VALID_BYTES = 40L * 1024 * 1024

    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<State?>(null)

    fun file(context: Context): File = File(File(context.filesDir, "whisper"), MODEL_NAME)

    fun isReady(context: Context): Boolean = file(context).let { it.exists() && it.length() >= MIN_VALID_BYTES }

    fun state(context: Context): StateFlow<State?> {
        if (_state.value == null || _state.value == State.Missing || _state.value == State.Ready) {
            _state.value = if (isReady(context)) State.Ready else State.Missing
        }
        return _state
    }

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Starts the download unless it's done, running, or (Wi-Fi only) not on Wi-Fi. Returns a message for the user. */
    fun download(context: Context, force: Boolean = false): String? {
        val app = context.applicationContext
        if (isReady(app)) {
            _state.value = State.Ready
            return null
        }
        if (job?.isActive == true) return null
        if (!force && Prefs.isWifiOnly(app) && !onWifi(app)) {
            _state.value = State.Failed("Waiting for Wi-Fi")
            return "The speech model downloads on Wi-Fi. Connect to Wi-Fi or turn off \"Only on Wi-Fi\"."
        }
        job = scope.launch {
            val target = file(app)
            val part = File(target.parentFile, "$MODEL_NAME.part")
            try {
                target.parentFile?.mkdirs()
                _state.value = State.Downloading(0)
                var url = URL(URL_MODEL)
                var conn = open(url)
                var hops = 0
                while (true) {
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 5) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        conn = open(url)
                        hops++
                        continue
                    }
                    if (code != 200) throw IllegalStateException("Server answered $code")
                    break
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = State.Downloading(percent)
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                if (part.length() < MIN_VALID_BYTES) throw IllegalStateException("Download was incomplete")
                if (!part.renameTo(target)) throw IllegalStateException("Couldn't save the model")
                _state.value = State.Ready
            } catch (e: Exception) {
                part.delete()
                _state.value = State.Failed(e.message ?: "Download failed")
            }
        }
        return null
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    fun delete(context: Context) {
        job?.cancel()
        Whisper.release()
        file(context).delete()
        _state.value = State.Missing
    }
}
