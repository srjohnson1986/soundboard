package com.example.soundboard.data

import androidx.test.core.app.ApplicationProvider
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
}
