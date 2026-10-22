package com.musictracker.audio

import android.os.SystemClock

/** The last 30 seconds of phone audio (16 kHz mono), kept in memory only. */
object AudioBuffer {
    const val SAMPLE_RATE = 16_000
    private const val SECONDS = 30

    private val data = FloatArray(SAMPLE_RATE * SECONDS)
    private var pos = 0
    private var filled = 0
    @Volatile private var lastWrite = 0L

    @Synchronized
    fun write(src: ShortArray, count: Int) {
        for (i in 0 until count) {
            data[pos] = src[i] / 32768f
            pos = (pos + 1) % data.size
        }
        filled = (filled + count).coerceAtMost(data.size)
        lastWrite = SystemClock.elapsedRealtime()
    }

    /** Copies out the most recent [seconds] of audio (less if not captured yet). */
    @Synchronized
    fun last(seconds: Int): FloatArray {
        val n = (seconds * SAMPLE_RATE).coerceAtMost(filled)
        val out = FloatArray(n)
        var start = pos - n
        if (start < 0) start += data.size
        for (i in 0 until n) out[i] = data[(start + i) % data.size]
        return out
    }

    @Synchronized
    fun clear() {
        pos = 0
        filled = 0
    }

    /** Whether audio arrived in the last few seconds. */
    fun isFlowing(): Boolean = SystemClock.elapsedRealtime() - lastWrite < 3_000
}
