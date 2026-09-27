package com.example.soundboard.web

import com.example.soundboard.data.OpfsFileStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * Recording and playback against the real browser APIs, with Chrome's fake microphone
 * (karma.config.d/fake-media.js). Waits are in real time, off the test's virtual clock.
 */
class WebAudioTest {

    private val files = OpfsFileStore(rootDirectory = "test-${Random.nextLong().toULong()}")

    private suspend fun realDelay(millis: Long) = withContext(Dispatchers.Default) { delay(millis) }

    /** A 24 kHz, 16-bit mono WAV's length in seconds, from its data chunk size. */
    private fun wavSeconds(bytes: ByteArray): Double {
        val dataBytes = (0 until 4).sumOf { (bytes[40 + it].toInt() and 0xFF) shl (8 * it) }
        return dataBytes / 48_000.0
    }

    private suspend fun recordFor(millis: Long, trimEndMillis: Int, path: String): ByteArray? {
        val recorder = WebRecorder(files)
        assertTrue(recorder.start(path), "recording should start")
        realDelay(millis)
        assertTrue(recorder.stop(trimEndMillis), "recording should stop with audio captured")
        return files.read(path)
    }

    @Test
    fun `a recording is saved as a WAV, and the player can decode it`() = runTest {
        val path = "sounds/clip.wav"
        val bytes = recordFor(1_500, trimEndMillis = 0, path = path)

        assertTrue(bytes != null && bytes.size > 1_000, "expected a saved clip, got ${bytes?.size} bytes")
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())

        val player = WebAudioPlayer(files, CoroutineScope(Dispatchers.Default))
        player.load("clip", path)
        repeat(50) { if (!player.isLoaded("clip")) realDelay(100) }
        assertTrue(player.isLoaded("clip"), "the player should decode the recording")
        player.play("clip", volume = 0.5f)
        player.release()
    }

    @Test
    fun `stopping trims the end of the recording, but never a short one down to nothing`() = runTest {
        val whole = wavSeconds(recordFor(1_500, trimEndMillis = 0, path = "sounds/whole.wav")!!)
        val trimmed = wavSeconds(recordFor(1_500, trimEndMillis = 500, path = "sounds/trimmed.wav")!!)
        val short = wavSeconds(recordFor(500, trimEndMillis = 500, path = "sounds/short.wav")!!)

        assertTrue(whole - trimmed in 0.3..0.7, "expected about 0.5 s less: whole $whole s, trimmed $trimmed s")
        assertTrue(short > 0.3, "a recording too short to trim is kept whole, got $short s")
    }

    @Test
    fun `a cancelled recording saves nothing, and stop without start fails`() = runTest {
        val recorder = WebRecorder(files)
        val path = "sounds/cancelled.${recorder.fileExtension}"

        assertTrue(recorder.start(path))
        realDelay(300)
        recorder.cancel()

        assertFalse(recorder.stop())
        assertFalse(files.exists(path))
    }
}
