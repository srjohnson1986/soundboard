package com.example.soundboard.web

import com.example.soundboard.data.OpfsFileStore
import kotlin.random.Random
import kotlin.test.Test
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

    @Test
    fun `a recording is saved, and the player can decode it`() = runTest {
        val recorder = WebRecorder(files)
        val path = "sounds/clip.${recorder.fileExtension}"

        assertTrue(recorder.start(path), "recording should start")
        realDelay(1_500)
        assertTrue(recorder.stop(), "recording should stop with audio captured")

        val bytes = files.read(path)
        assertTrue(bytes != null && bytes.size > 1_000, "expected a saved clip, got ${bytes?.size} bytes")

        val player = WebAudioPlayer(files, CoroutineScope(Dispatchers.Default))
        player.load("clip", path)
        repeat(50) { if (!player.isLoaded("clip")) realDelay(100) }
        assertTrue(player.isLoaded("clip"), "the player should decode the recording")
        player.play("clip", volume = 0.5f)
        player.release()
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
