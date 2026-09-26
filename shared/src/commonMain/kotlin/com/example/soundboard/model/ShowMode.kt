package com.example.soundboard.model

/**
 * Show mode: a tapped tile's words fill the screen in large white-on-black text, for
 * someone to read instead of (or as well as) hear. Per-device, not part of [Board] — see
 * [com.example.soundboard.data.DevicePreferences].
 *
 * At least one way out always stays on: [timerSeconds] (0 = no timer) and [tapToClose]
 * can't both be off, or the text screen would never go away (Back still closes it, but
 * that's a safety exit, not something to rely on). [withTimerSeconds] and [withTapToClose]
 * refuse the change that would break that.
 */
data class ShowModeSettings(
    val enabled: Boolean = false,
    val timerSeconds: Int = DEFAULT_TIMER_SECONDS,
    val tapToClose: Boolean = true,
    /** Skips the tile's sound or speech while showing its text. */
    val muteSounds: Boolean = false,
    /** Draws the text upside down, for someone sitting across from the device to read. */
    val flipped: Boolean = false
) {
    val hasTimer: Boolean get() = timerSeconds > 0

    /** A copy with the timer set to [seconds] (0 = off), unless that would leave no way to close the text. */
    fun withTimerSeconds(seconds: Int): ShowModeSettings =
        if (seconds <= 0 && !tapToClose) this else copy(timerSeconds = seconds.coerceAtLeast(0))

    /** A copy with tap-to-close set to [value], unless that would leave no way to close the text. */
    fun withTapToClose(value: Boolean): ShowModeSettings =
        if (!value && !hasTimer) this else copy(tapToClose = value)

    companion object {
        const val DEFAULT_TIMER_SECONDS = 10

        /** Timer choices Settings offers; 0 means "Off". */
        val TIMER_OPTIONS_SECONDS = listOf(0, 3, 5, 10, 15, 30)
    }
}
