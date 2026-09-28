package com.example.soundboard.data

import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.SpeechSettings

/**
 * Settings that describe this device rather than the board's content — deliberately
 * kept in device-local storage ([KeyValueStore]; SharedPreferences on Android) instead of on [com.example.soundboard.model.Board],
 * since the rest of Settings intentionally travels with the board (switching to a
 * different board switches those too). Performance mode shouldn't silently flip off
 * just because the board you switched to didn't have it set, and neither should Show mode,
 * which is about where the board is being used, not what's on it.
 */
class DevicePreferences(private val prefs: KeyValueStore) {

    /** Disables tile shadows, which are otherwise drawn on every filled tile all the time. */
    var performanceModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERFORMANCE_MODE_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_PERFORMANCE_MODE_ENABLED, value)

    /**
     * How much of the end of each new recording to leave off, in milliseconds: the tap on Stop
     * is otherwise the last thing on it (#204). Per device, since how loud that tap is depends
     * on the device. 0 keeps every recording whole.
     */
    var recordingTrimEndMillis: Int
        get() = prefs.getInt(KEY_RECORDING_TRIM_END_MILLIS, DEFAULT_RECORDING_TRIM_END_MILLIS).coerceAtLeast(0)
        set(value) = prefs.putInt(KEY_RECORDING_TRIM_END_MILLIS, value.coerceAtLeast(0))

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
        set(value) {
            prefs.putBoolean(KEY_SHOW_MODE_ENABLED, value.enabled)
            prefs.putInt(KEY_SHOW_MODE_TIMER_SECONDS, value.timerSeconds)
            prefs.putBoolean(KEY_SHOW_MODE_TAP_TO_CLOSE, value.tapToClose)
            prefs.putBoolean(KEY_SHOW_MODE_MUTE_SOUNDS, value.muteSounds)
            prefs.putBoolean(KEY_SHOW_MODE_FLIPPED, value.flipped)
        }

    /** The voice, speed and pitch speaking tiles use on this device (#250). */
    var speech: SpeechSettings
        get() = SpeechSettings(
            voiceId = prefs.getString(KEY_SPEECH_VOICE_ID),
            ratePercent = prefs.getInt(KEY_SPEECH_RATE_PERCENT, 100).coerceIn(SpeechSettings.PERCENT_RANGE),
            pitchPercent = prefs.getInt(KEY_SPEECH_PITCH_PERCENT, 100).coerceIn(SpeechSettings.PERCENT_RANGE)
        )
        set(value) {
            prefs.putString(KEY_SPEECH_VOICE_ID, value.voiceId)
            prefs.putInt(KEY_SPEECH_RATE_PERCENT, value.ratePercent.coerceIn(SpeechSettings.PERCENT_RANGE))
            prefs.putInt(KEY_SPEECH_PITCH_PERCENT, value.pitchPercent.coerceIn(SpeechSettings.PERCENT_RANGE))
        }

    /**
     * When the board first changed after its last backup, in epoch milliseconds; null while
     * everything on it is in a backup (#249). Per device, like the recordings it protects.
     */
    var unbackedChangesSince: Long?
        get() = prefs.getString(KEY_UNBACKED_CHANGES_SINCE)?.toLongOrNull()
        set(value) = prefs.putString(KEY_UNBACKED_CHANGES_SINCE, value?.toString())

    /**
     * The caregiver lock (#251): with it on, the board starts locked, with its editing
     * hidden until someone holds Unlock. Per device, so it doesn't travel in a backup.
     */
    var editingLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_EDITING_LOCK_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_EDITING_LOCK_ENABLED, value)

    /** Until when "Later" on the backup reminder keeps it away, in epoch milliseconds. */
    var backupReminderSnoozedUntil: Long?
        get() = prefs.getString(KEY_BACKUP_REMINDER_SNOOZED_UNTIL)?.toLongOrNull()
        set(value) = prefs.putString(KEY_BACKUP_REMINDER_SNOOZED_UNTIL, value?.toString())

    companion object {
        /** The SharedPreferences file these live in on Android. */
        const val PREFS_NAME = "device_preferences"

        const val DEFAULT_RECORDING_TRIM_END_MILLIS = 250

        /** Choices Settings offers for [recordingTrimEndMillis]; 0 means "Off". */
        val RECORDING_TRIM_OPTIONS_MILLIS = listOf(0, 100, 250, 500)

        private const val KEY_RECORDING_TRIM_END_MILLIS = "recording_trim_end_millis"

        private const val KEY_PERFORMANCE_MODE_ENABLED = "performance_mode_enabled"
        private const val KEY_SHOW_MODE_ENABLED = "show_mode_enabled"
        private const val KEY_SHOW_MODE_TIMER_SECONDS = "show_mode_timer_seconds"
        private const val KEY_SHOW_MODE_TAP_TO_CLOSE = "show_mode_tap_to_close"
        private const val KEY_SHOW_MODE_MUTE_SOUNDS = "show_mode_mute_sounds"
        private const val KEY_SHOW_MODE_FLIPPED = "show_mode_flipped"
        private const val KEY_SPEECH_VOICE_ID = "speech_voice_id"
        private const val KEY_UNBACKED_CHANGES_SINCE = "unbacked_changes_since"
        private const val KEY_EDITING_LOCK_ENABLED = "editing_lock_enabled"
        private const val KEY_BACKUP_REMINDER_SNOOZED_UNTIL = "backup_reminder_snoozed_until"
        private const val KEY_SPEECH_RATE_PERCENT = "speech_rate_percent"
        private const val KEY_SPEECH_PITCH_PERCENT = "speech_pitch_percent"
    }
}
