package com.example.soundboard.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The end-of-recording trim (#204), on the device's real MediaExtractor and MediaMuxer. */
@RunWith(AndroidJUnit4::class)
class AudioTrimTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "trim-test").apply { mkdirs() }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    // One AAC frame is about 23 ms; allow a few.
    private fun assertClose(expectedMs: Long, actualMs: Long, what: String) =
        assertTrue("$what: expected ~$expectedMs ms, was $actualMs ms", abs(expectedMs - actualMs) <= 70)

    @Test
    fun trimmingLeavesOffTheEndAndKeepsTheRestPlayable() {
        val file = File(dir, "clip.m4a")
        TestAudioFiles.writeAacM4a(file, durationMs = 2_000)
        val beforeMs = TestAudioFiles.playbackDurationMs(file)

        assertTrue(trimAudioEnd(file, trimUs = 250_000))

        assertClose(beforeMs - 250, TestAudioFiles.playbackDurationMs(file), "playback duration")
        assertClose(beforeMs - 250, TestAudioFiles.trackDurationUs(file) / 1_000, "track duration")
        assertFalse(File(dir, "clip.m4a.trimmed").exists())
    }

    @Test
    fun aClipTooShortToSpareTheTrimIsKeptWhole() {
        val file = File(dir, "short.m4a")
        TestAudioFiles.writeAacM4a(file, durationMs = 450)
        val before = file.readBytes()

        // 450 ms less 250 ms would leave less than the 300 ms minimum.
        assertFalse(trimAudioEnd(file, trimUs = 250_000))

        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun aFileThatIsntAudioIsLeftAsItIs() {
        val file = File(dir, "broken.m4a").apply { writeText("not audio") }

        assertFalse(trimAudioEnd(file, trimUs = 250_000))

        assertEquals("not audio", file.readText())
        assertFalse(File(dir, "broken.m4a.trimmed").exists())
    }
}
