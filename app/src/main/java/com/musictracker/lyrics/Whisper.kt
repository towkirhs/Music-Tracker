package com.musictracker.lyrics

/** Thin wrapper over whisper.cpp (built from source by app/src/main/cpp). */
object Whisper {
    private var handle = 0L
    private var loadedPath: String? = null
    private val libraryLoaded: Boolean = runCatching { System.loadLibrary("whisperjni") }.isSuccess

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, threads: Int): String
    private external fun nativeFree(handle: Long)

    /** Loads the model once; later calls are free. */
    @Synchronized
    fun ensureLoaded(path: String): Boolean {
        if (!libraryLoaded) return false
        if (handle != 0L && loadedPath == path) return true
        release()
        handle = runCatching { nativeInit(path) }.getOrDefault(0L)
        loadedPath = if (handle != 0L) path else null
        return handle != 0L
    }

    /** Returns (language code, text), or null if transcription failed. */
    @Synchronized
    fun transcribe(samples: FloatArray): Pair<String, String>? {
        if (handle == 0L) return null
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val out = runCatching { nativeTranscribe(handle, samples, threads) }.getOrNull() ?: return null
        val tab = out.indexOf('\t')
        if (tab < 0) return null
        return out.substring(0, tab) to out.substring(tab + 1).trim()
    }

    @Synchronized
    fun release() {
        if (handle != 0L) runCatching { nativeFree(handle) }
        handle = 0L
        loadedPath = null
    }
}
