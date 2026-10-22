package com.musictracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.musictracker.audio.AudioCaptureService
import com.musictracker.audio.Notifications
import com.musictracker.ui.MusicTrackerApp
import com.musictracker.ui.MusicTrackerTheme

class MainActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            AudioCaptureService.start(this, r.resultCode, data)
        } else {
            Toast.makeText(this, "Audio not allowed: songs without a name on screen won't be recognised", Toast.LENGTH_LONG).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasRecordPermission()) askForAudio() else {
            Toast.makeText(this, "Music Tracker needs audio permission to hear lyrics", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicTrackerTheme {
                MusicTrackerApp(onRequestAudio = ::requestAudio)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Notifications.ACTION_RESUME_AUDIO) requestAudio()
    }

    private fun hasRecordPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Asks for the permissions, then Android's "start recording?" consent, then starts listening to audio. */
    fun requestAudio() {
        if (AudioCaptureService.running.value) return
        val needed = buildList {
            if (!hasRecordPermission()) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray()) else askForAudio()
    }

    private fun askForAudio() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        // Whole screen, so audio from every app can be heard (single-app sharing would limit it).
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        runCatching { projectionLauncher.launch(intent) }
    }
}
