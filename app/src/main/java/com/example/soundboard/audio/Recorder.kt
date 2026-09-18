package com.example.soundboard.audio

import java.io.File

interface Recorder {
    /** Starts recording into [file]; returns false if the recorder couldn't start. */
    fun start(file: File): Boolean

    /** Stops the current recording; returns false if nothing usable was captured. */
    fun stop(): Boolean

    /** Abandons the current recording without keeping whatever it captured. */
    fun cancel()
}
