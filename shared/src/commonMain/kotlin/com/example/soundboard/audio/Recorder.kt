package com.example.soundboard.audio

interface Recorder {
    /** The file extension this recorder's clips are saved with, without the dot, e.g. "m4a". */
    val fileExtension: String

    /**
     * Starts recording into [path] (a [com.example.soundboard.data.FileStore] path); returns false
     * if the recorder couldn't start. Suspends because a browser only grants the microphone
     * asynchronously, after asking the user.
     */
    suspend fun start(path: String): Boolean

    /** Stops the current recording and saves it; returns false if nothing usable was captured. */
    suspend fun stop(): Boolean

    /** Abandons the current recording without keeping whatever it captured. */
    fun cancel()
}
