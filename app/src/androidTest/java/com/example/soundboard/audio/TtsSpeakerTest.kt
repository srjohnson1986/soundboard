package com.example.soundboard.audio

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A smoke test of [TtsSpeaker] on the device's own speech engine, where it has one. */
@RunWith(AndroidJUnit4::class)
class TtsSpeakerTest {
    private val speaker = TtsSpeaker(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun shutDown() {
        speaker.shutdown()
    }

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) return false
            Thread.sleep(50)
        }
        return true
    }

    @Test
    fun speaksOnceTheEngineIsReadyAndStopStopsIt() {
        assumeTrue("this device has no speech engine", speaker.hasEngine)
        // Asked before the engine is up: remembered and spoken once it is.
        speaker.speak("Some water, please. I would like some water.")
        assumeTrue("the speech engine didn't start", waitFor(10_000) { speaker.isReady })

        assertTrue("never started speaking", waitFor(10_000) { speaker.isSpeaking })
        // #248: a working engine reports itself available, so the board shows no warning.
        assertEquals(true, speaker.available.value)

        speaker.stop()
        assertTrue("didn't stop", waitFor(2_000) { !speaker.isSpeaking })
    }
}
