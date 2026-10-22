package com.musictracker.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hears what other apps play (Android's playback capture, which needs the screen-recording consent).
 * Keeps the last 30 seconds in memory for lyrics recognition; nothing is saved or sent anywhere.
 * Android ends the session when the screen locks; we then post a one-tap "resume" notification.
 */
class AudioCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    @Volatile private var active = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (data == null || active) {
            if (!active) stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, Notifications.ID_LISTENING, Notifications.listening(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { onProjectionStopped() }
            }
        }, handler)
        if (!startRecording(p)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Notifications.clearResume(this)
        _running.value = true
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(p: MediaProjection): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val config = AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioBuffer.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minSize = AudioRecord.getMinBufferSize(
            AudioBuffer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(3200)
        val r = runCatching {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minSize * 4)
                .setAudioPlaybackCaptureConfig(config)
                .build()
        }.getOrNull() ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        record = r
        AudioBuffer.clear()
        r.startRecording()
        active = true
        reader = Thread({
            val chunk = ShortArray(1600)
            while (active) {
                val n = r.read(chunk, 0, chunk.size)
                if (n > 0) AudioBuffer.write(chunk, n) else if (n < 0) break
            }
        }, "music-tracker-audio").also { it.start() }
        return true
    }

    private fun onProjectionStopped() {
        val wasActive = active
        cleanup()
        if (wasActive && Prefs.isListening(this) && Prefs.isLyricsOn(this)) Notifications.showResume(this)
        stopSelf()
    }

    private fun cleanup() {
        active = false
        runCatching { reader?.join(500) }
        reader = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        val p = projection
        projection = null
        runCatching { p?.stop() }
        _running.value = false
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioCaptureService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioCaptureService::class.java))
        }
    }
}
