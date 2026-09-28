package com.example.soundboard.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device's media volume, which clips and speech both play at, is turned all the
 * way down: the whole board is silent then, and the board says so (#248).
 */
interface MediaVolume {
    val muted: StateFlow<Boolean>

    /** Checks again now, e.g. as a tile is tapped, in case a change went unreported. */
    fun refresh() {}

    fun release() {}
}

/** For a platform that can't read the system volume, such as a browser: never reported as off. */
object UnknownMediaVolume : MediaVolume {
    override val muted: StateFlow<Boolean> = MutableStateFlow(false)
}
