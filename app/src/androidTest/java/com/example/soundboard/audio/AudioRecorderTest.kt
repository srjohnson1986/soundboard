package com.example.soundboard.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.soundboard.data.FileSystemStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AudioRecorder] on the device's real MediaRecorder. An emulator's microphone records
 * silence, which is enough to check the file and the trim; it says nothing about voice
 * quality (see ARCHITECTURE.md, "Known limitations").
 */
@RunWith(AndroidJUnit4::class)
class AudioRecorderTest {
    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "recorder-test").apply { mkdirs() }
    private val recorder = AudioRecorder(context, FileSystemStore(root))

    @After
    fun cleanUp() {
        recorder.cancel()
        root.deleteRecursively()
    }

    private fun record(path: String, millis: Long, trimEndMillis: Int): Boolean = runBlocking {
        // A device or emulator without a microphone can't run these.
        assumeTrue("no microphone to record from", recorder.start(path))
        Thread.sleep(millis)
        recorder.stop(trimEndMillis)
    }

    @Test
    fun aRealRecordingTrimsByTheAmountAskedToTheNearestFrame() {
        assertTrue(record("sounds/whole.m4a", millis = 1_500, trimEndMillis = 0))
        val whole = File(root, "sounds/whole.m4a")
        val copy = File(root, "sounds/copy.m4a").also { whole.copyTo(it) }

        assertTrue(trimAudioEnd(copy, trimUs = 500_000))

        // The trim keeps whole frames, so it cuts up to one frame less than asked.
        val frame = TestAudioFiles.aacFrameMs(whole)
        val cut = TestAudioFiles.playbackDurationMs(whole) - TestAudioFiles.playbackDurationMs(copy)
        assertTrue("cut $cut ms with $frame ms frames", cut in (500 - frame)..(500 + frame))
    }

    @Test
    fun stopLeavesOffTheTrim() {
        assertTrue(record("sounds/trimmed.m4a", millis = 2_000, trimEndMillis = 1_000))

        // Without the trim this would be about 1.6 to 2 s long.
        val file = File(root, "sounds/trimmed.m4a")
        val kept = TestAudioFiles.playbackDurationMs(file)
        assertTrue("kept $kept ms", kept in 300..(1_000 + TestAudioFiles.aacFrameMs(file) + 50))
    }

    @Test
    fun cancelLeavesNothingToStop() = runBlocking {
        assumeTrue("no microphone to record from", recorder.start("sounds/cancelled.m4a"))
        Thread.sleep(300)

        recorder.cancel()

        assertFalse(recorder.stop(trimEndMillis = 0))
    }
}
