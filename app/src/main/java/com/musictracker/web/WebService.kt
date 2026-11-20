package com.musictracker.web

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.musictracker.audio.Notifications
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address

/** Keeps the website running on your home Wi-Fi while you have it switched on. */
class WebService : Service() {

    private var server: WebServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = address(this)
        ServiceCompat.startForeground(
            this, Notifications.ID_WEB, Notifications.web(this, address ?: "this phone's address"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (server == null) {
            server = runCatching { WebServer(applicationContext, PORT).apply { start(SOCKET_READ_TIMEOUT_MS, false) } }
                .onFailure { stopSelf() }
                .getOrNull()
        }
        _running.value = server != null
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        _running.value = false
        super.onDestroy()
    }

    companion object {
        const val PORT = 8080
        private const val SOCKET_READ_TIMEOUT_MS = 15_000

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            Prefs.setWebOn(context, true)
            ContextCompat.startForegroundService(context, Intent(context, WebService::class.java))
        }

        fun stop(context: Context) {
            Prefs.setWebOn(context, false)
            context.stopService(Intent(context, WebService::class.java))
        }

        /** "http://192.168.1.5:8080" on Wi-Fi, or null when the phone has no local address. */
        fun address(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val ip = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
                ?: return null
            return "http://$ip:$PORT"
        }
    }
}
