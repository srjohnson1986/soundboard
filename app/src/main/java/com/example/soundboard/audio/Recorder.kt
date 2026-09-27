package com.example.soundboard.audio

interface Recorder {
    /** Starts recording into [path] (a [com.example.soundboard.data.FileStore] path); returns false if the recorder couldn't start. */
    fun start(path: String): Boolean

    /** Stops the current recording; returns false if nothing usable was captured. */
    fun stop(): Boolean

    /** Abandons the current recording without keeping whatever it captured. */
    fun cancel()
}
