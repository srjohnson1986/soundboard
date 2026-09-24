package com.example.soundboard.data

import android.content.Context

/**
 * Settings that describe this device rather than the board's content — deliberately
 * kept in device-local SharedPreferences instead of on [com.example.soundboard.model.Board],
 * since the rest of Settings intentionally travels with the board (switching to a
 * different board switches those too). Performance mode shouldn't silently flip off
 * just because the board you switched to didn't have it set.
 */
class DevicePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Disables tile shadows, which are otherwise drawn on every filled tile all the time. */
    var performanceModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERFORMANCE_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERFORMANCE_MODE_ENABLED, value).apply()

    private companion object {
        const val PREFS_NAME = "device_preferences"
        const val KEY_PERFORMANCE_MODE_ENABLED = "performance_mode_enabled"
    }
}
