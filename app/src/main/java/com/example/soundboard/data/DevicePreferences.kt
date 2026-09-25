package com.example.soundboard.data

import android.content.Context
import com.example.soundboard.model.ShowModeSettings

/**
 * Settings that describe this device rather than the board's content — deliberately
 * kept in device-local SharedPreferences instead of on [com.example.soundboard.model.Board],
 * since the rest of Settings intentionally travels with the board (switching to a
 * different board switches those too). Performance mode shouldn't silently flip off
 * just because the board you switched to didn't have it set, and neither should Show mode,
 * which is about where the board is being used, not what's on it.
 */
class DevicePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Disables tile shadows, which are otherwise drawn on every filled tile all the time. */
    var performanceModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERFORMANCE_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERFORMANCE_MODE_ENABLED, value).apply()

    /**
     * Show mode and its options. A stored combination with no way to close the text (no timer
     * and no tap to close) reads back with tap to close on, so it can never strand anyone.
     */
    var showMode: ShowModeSettings
        get() {
            val defaults = ShowModeSettings()
            val timerSeconds = prefs.getInt(KEY_SHOW_MODE_TIMER_SECONDS, defaults.timerSeconds).coerceAtLeast(0)
            return ShowModeSettings(
                enabled = prefs.getBoolean(KEY_SHOW_MODE_ENABLED, defaults.enabled),
                timerSeconds = timerSeconds,
                tapToClose = prefs.getBoolean(KEY_SHOW_MODE_TAP_TO_CLOSE, defaults.tapToClose) || timerSeconds == 0,
                muteSounds = prefs.getBoolean(KEY_SHOW_MODE_MUTE_SOUNDS, defaults.muteSounds),
                flipped = prefs.getBoolean(KEY_SHOW_MODE_FLIPPED, defaults.flipped)
            )
        }
        set(value) = prefs.edit()
            .putBoolean(KEY_SHOW_MODE_ENABLED, value.enabled)
            .putInt(KEY_SHOW_MODE_TIMER_SECONDS, value.timerSeconds)
            .putBoolean(KEY_SHOW_MODE_TAP_TO_CLOSE, value.tapToClose)
            .putBoolean(KEY_SHOW_MODE_MUTE_SOUNDS, value.muteSounds)
            .putBoolean(KEY_SHOW_MODE_FLIPPED, value.flipped)
            .apply()

    private companion object {
        const val PREFS_NAME = "device_preferences"
        const val KEY_PERFORMANCE_MODE_ENABLED = "performance_mode_enabled"
        const val KEY_SHOW_MODE_ENABLED = "show_mode_enabled"
        const val KEY_SHOW_MODE_TIMER_SECONDS = "show_mode_timer_seconds"
        const val KEY_SHOW_MODE_TAP_TO_CLOSE = "show_mode_tap_to_close"
        const val KEY_SHOW_MODE_MUTE_SOUNDS = "show_mode_mute_sounds"
        const val KEY_SHOW_MODE_FLIPPED = "show_mode_flipped"
    }
}
