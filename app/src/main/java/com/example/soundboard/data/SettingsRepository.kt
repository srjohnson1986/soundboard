package com.example.soundboard.data

import android.content.Context

/** Small app-level preferences that aren't board content (see [BoardRepository]), stored outside board.json so they survive switching/importing boards. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Whether a fresh launch should jump straight to the home page instead of resuming the last-viewed one. */
    var openOnHomePage: Boolean
        get() = prefs.getBoolean(KEY_OPEN_ON_HOME_PAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_ON_HOME_PAGE, value).apply()

    private companion object {
        const val KEY_OPEN_ON_HOME_PAGE = "open_on_home_page"
    }
}
