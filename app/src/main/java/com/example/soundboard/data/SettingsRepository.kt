package com.example.soundboard.data

import android.content.Context

/** Small app-level preferences that aren't board content (see [BoardRepository]), stored outside board.json so they survive switching/importing boards. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether a fresh launch should jump straight to the home page instead of resuming the last-viewed one. */
    var openOnHomePage: Boolean
        get() = prefs.getBoolean(KEY_OPEN_ON_HOME_PAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_ON_HOME_PAGE, value).apply()

    /** Minutes of inactivity before the board auto-returns to its home page; 0 disables auto-return. */
    var idleTimeoutMinutes: Int
        get() = prefs.getInt(KEY_IDLE_TIMEOUT_MINUTES, DEFAULT_IDLE_TIMEOUT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_IDLE_TIMEOUT_MINUTES, value).apply()

    /** How long a page-tab press must be held before it's treated as a long-press, in milliseconds. */
    var longPressDurationMillis: Int
        get() = prefs.getInt(KEY_LONG_PRESS_DURATION_MILLIS, DEFAULT_LONG_PRESS_DURATION_MILLIS)
        set(value) = prefs.edit().putInt(KEY_LONG_PRESS_DURATION_MILLIS, value).apply()

    private companion object {
        const val KEY_OPEN_ON_HOME_PAGE = "open_on_home_page"
        const val KEY_IDLE_TIMEOUT_MINUTES = "idle_timeout_minutes"
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 5
        const val KEY_LONG_PRESS_DURATION_MILLIS = "long_press_duration_millis"
        /** Matches the Android platform default (ViewConfiguration.longPressTimeoutMillis). */
        const val DEFAULT_LONG_PRESS_DURATION_MILLIS = 500
    }
}
