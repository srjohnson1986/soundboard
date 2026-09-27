package com.example.soundboard.web

import com.example.soundboard.data.OpfsFileStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * Recording and playback against the real browser APIs, with Chrome's fake microphone
 * (karma.config.d/fake-media.js). Waits are in real time, off the test's virtual clock.
 * How long a real recording is depends on how quickly the recorder starts, so the trim is
 * tested exactly on a known buffer, and on a real recording only against a bound.
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

    /** A WAV's 16-bit samples, scaled to -1..1. */
    private fun wavSamples(bytes: ByteArray): List<Double> = (44 until bytes.size step 2).map {
        ((bytes[it].toInt() and 0xFF) or (bytes[it + 1].toInt() shl 8)) / 32_768.0
    }

    @Test
    fun `the trim cuts exactly the amount asked from the end`() = runTest {
        val whole = wavSamples(trimmedWav(ramp(1.5), trimSeconds = 0.0))
        val trimmed = wavSamples(trimmedWav(ramp(1.5), trimSeconds = 0.5))

        assertEquals(36_000, whole.size, "1.5 s at 24 kHz")
        assertEquals(24_000, trimmed.size, "1.0 s at 24 kHz")
        // The ramp rises from 0 to 0.9 over 1.5 s, so the kept part starts at 0 and ends at 0.6.
        assertEquals(0.0, trimmed.first(), absoluteTolerance = 0.01)
        assertEquals(0.6, trimmed.last(), absoluteTolerance = 0.01)
    }

    @Test
    fun `a clip too short to trim is kept whole`() = runTest {
        assertEquals(12_000, wavSamples(trimmedWav(ramp(0.5), trimSeconds = 0.5)).size)
        assertEquals(16_800, wavSamples(trimmedWav(ramp(0.7), trimSeconds = 0.5)).size)
    }

    @Test
    fun `stopping leaves off the trim`() = runTest {
        val recorder = WebRecorder(files)
        val begun = TimeSource.Monotonic.markNow()
        assertTrue(recorder.start("sounds/trimmed.wav"), "recording should start")
        realDelay(2_000)
        val recorded = begun.elapsedNow().inWholeMilliseconds / 1000.0
        assertTrue(recorder.stop(trimEndMillis = 1_000), "recording should stop with audio captured")

        // Nothing recorded can be longer than the time since start() was called, so without
        // the trim this would be more than 1 s over the bound (an Opus frame is 20 ms).
        val kept = wavSeconds(files.read("sounds/trimmed.wav")!!)
        assertTrue(kept > 0.0 && kept <= recorded - 1.0 + 0.1, "kept $kept s of $recorded s")
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

/**
 * A mono 48 kHz AudioBuffer of [seconds] whose samples rise steadily from 0 to 0.9, so the
 * last sample left after a trim shows where the cut fell.
 */
private fun ramp(seconds: Double): JsAny = js(
    """(() => {
        const rate = 48000;
        const length = Math.round(seconds * rate);
        const buffer = new AudioBuffer({ length: length, sampleRate: rate, numberOfChannels: 1 });
        const data = buffer.getChannelData(0);
        for (let i = 0; i < length; i++) data[i] = 0.9 * i / length;
        return buffer;
    })()"""
)
