package com.example.soundboard.data

import com.example.soundboard.model.ShowModeSettings
import com.example.soundboard.model.SpeechSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each "fresh instance" reads the same store, the way the next launch reads the last one's. */
class DevicePreferencesTest {

    private val store = MapKeyValueStore()

    private fun prefs() = DevicePreferences(store)

    @Test
    fun `performanceModeEnabled defaults to false and persists across a fresh instance`() {
        assertFalse(prefs().performanceModeEnabled)

        prefs().performanceModeEnabled = true

        assertTrue(prefs().performanceModeEnabled)
    }

    @Test
    fun `showMode defaults to off with a 10 second timer, tap to close, sounds on, and not flipped`() {
        assertEquals(
            ShowModeSettings(enabled = false, timerSeconds = 10, tapToClose = true, muteSounds = false, flipped = false),
            prefs().showMode
        )
    }

    @Test
    fun `showMode persists across a fresh instance`() {
        val settings = ShowModeSettings(enabled = true, timerSeconds = 3, tapToClose = false, muteSounds = true, flipped = true)

        prefs().showMode = settings

        assertEquals(settings, prefs().showMode)
    }

    @Test
    fun `a stored showMode with no way to close reads back with tap to close on`() {
        prefs().showMode = ShowModeSettings(enabled = true, timerSeconds = 0, tapToClose = false)

        assertTrue(prefs().showMode.tapToClose)
    }

    @Test
    fun `recording trim defaults to a quarter second and persists across a fresh instance`() {
        assertEquals(250, prefs().recordingTrimEndMillis)

        prefs().recordingTrimEndMillis = 500

        assertEquals(500, prefs().recordingTrimEndMillis)
    }

    @Test
    fun `speech defaults to the device's voice at normal speed and pitch, and persists`() {
        assertEquals(SpeechSettings(), prefs().speech)

        prefs().speech = SpeechSettings(voiceId = "en-us-x-iob-local", ratePercent = 75, pitchPercent = 125)

        assertEquals(SpeechSettings(voiceId = "en-us-x-iob-local", ratePercent = 75, pitchPercent = 125), prefs().speech)
    }

    @Test
    fun `going back to the default voice forgets the chosen one, and odd speeds are kept in range`() {
        prefs().speech = SpeechSettings(voiceId = "some-voice")
        prefs().speech = SpeechSettings(voiceId = null, ratePercent = 5, pitchPercent = 9_000)

        assertEquals(SpeechSettings(voiceId = null, ratePercent = 25, pitchPercent = 400), prefs().speech)
    }
}
