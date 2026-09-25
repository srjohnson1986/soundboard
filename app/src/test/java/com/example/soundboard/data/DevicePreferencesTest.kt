package com.example.soundboard.data

import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.model.ShowModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevicePreferencesTest {

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `performanceModeEnabled defaults to false`() {
        val prefs = DevicePreferences(context)

        assertFalse(prefs.performanceModeEnabled)
    }

    @Test
    fun `performanceModeEnabled round-trips and persists across a fresh instance`() {
        DevicePreferences(context).performanceModeEnabled = true

        assertTrue(DevicePreferences(context).performanceModeEnabled)
    }

    @Test
    fun `showMode defaults to off with a 10 second timer, tap to close, sounds on, and not flipped`() {
        assertEquals(
            ShowModeSettings(enabled = false, timerSeconds = 10, tapToClose = true, muteSounds = false, flipped = false),
            DevicePreferences(context).showMode
        )
    }

    @Test
    fun `showMode round-trips and persists across a fresh instance`() {
        val settings = ShowModeSettings(enabled = true, timerSeconds = 3, tapToClose = false, muteSounds = true, flipped = true)

        DevicePreferences(context).showMode = settings

        assertEquals(settings, DevicePreferences(context).showMode)
    }

    @Test
    fun `a stored showMode with no way to close reads back with tap to close on`() {
        DevicePreferences(context).showMode = ShowModeSettings(enabled = true, timerSeconds = 0, tapToClose = false)

        assertTrue(DevicePreferences(context).showMode.tapToClose)
    }
}
