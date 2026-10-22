package com.musictracker.tracking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.musictracker.data.LabelSample
import com.musictracker.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Detector 1. Android lets this service read the text on screen in the short-video apps;
 * it looks for the song label and reports it to the PlayTracker. Does nothing while stopped.
 */
class LabelReaderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastScan = 0L
    private var scanPending = false
    private var lastLabDump = 0L
    private var lastLabSet: Set<String> = emptySet()

    /** Packages whose windows pop up over a reel without meaning you left it. */
    private val ignoredPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.nothing.launcher",
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) PlayTracker.end(PlayTracker.Source.VIDEO)
        }
    }

    private val scanRunnable = Runnable {
        scanPending = false
        lastScan = SystemClock.elapsedRealtime()
        runCatching { scan() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !Prefs.isListening(this)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in LabelParser.WATCHED) {
            // Another app came to the front: the reel is no longer playing.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                pkg != packageName && pkg !in ignoredPackages
            ) {
                PlayTracker.observe(this, PlayTracker.Source.VIDEO, null)
            }
            return
        }
        if (scanPending) return
        scanPending = true
        val wait = (800 - (SystemClock.elapsedRealtime() - lastScan)).coerceAtLeast(0)
        handler.postDelayed(scanRunnable, wait)
    }

    private fun scan() {
        if (!Prefs.isListening(this)) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val appName = LabelParser.WATCHED[pkg]
        if (appName == null) {
            if (pkg !in ignoredPackages) PlayTracker.observe(this, PlayTracker.Source.VIDEO, null)
            return
        }
        val texts = collect(root)
        val heard = when (val result = LabelParser.parse(appName, texts)) {
            is LabelParser.Result.Song -> Heard(result.title, result.artist, result.raw, appName, "label")
            else -> null
        }
        PlayTracker.observe(this, PlayTracker.Source.VIDEO, heard)
        if (Prefs.isLabOn(this)) saveLabSample(appName, texts)
    }

    /** Walks the visible views and collects their text and descriptions. */
    private fun collect(root: AccessibilityNodeInfo): List<ScreenText> {
        val out = ArrayList<ScreenText>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 2500) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisibleToUser) continue
            val id = node.viewIdResourceName.orEmpty()
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += ScreenText(it, id, false) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += ScreenText(it, id, true) }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    /** Label lab: every few seconds, saves what is on screen so the rules can be tuned. */
    private fun saveLabSample(appName: String, texts: List<ScreenText>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLabDump < 4000) return
        lastLabDump = now
        val lines = texts.map { (if (it.isDescription) "[desc] " else "") + it.text }.toSet()
        val fresh = texts.filter { (if (it.isDescription) "[desc] " else "") + it.text !in lastLabSet }
        lastLabSet = lines
        if (fresh.isEmpty()) return
        val wall = System.currentTimeMillis()
        val samples = fresh.map {
            LabelSample(
                capturedAt = wall,
                sourceApp = appName,
                viewId = it.viewId.substringAfter(":id/"),
                content = (if (it.isDescription) "[desc] " else "") + it.text.take(300),
            )
        }
        val context = applicationContext
        scope.launch { runCatching { Repo.saveSamples(context, samples) } }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        handler.removeCallbacks(scanRunnable)
        PlayTracker.end(PlayTracker.Source.VIDEO)
        scope.cancel()
        super.onDestroy()
    }
}
