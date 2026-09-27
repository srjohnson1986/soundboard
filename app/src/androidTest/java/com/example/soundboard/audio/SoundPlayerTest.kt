package com.example.soundboard.audio

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.soundboard.data.FileSystemStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A smoke test of [SoundPlayer] on the device's real SoundPool and MediaPlayer. */
@RunWith(AndroidJUnit4::class)
class SoundPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = File(instrumentation.targetContext.cacheDir, "player-test").apply { mkdirs() }

    // A low threshold, so the long clip can stay small: over it goes through MediaPlayer.
    private val player = SoundPlayer(FileSystemStore(root), longClipThresholdBytes = 50_000L)

    init {
        TestAudioFiles.writeWav(File(root, "sounds/short.wav"), durationMs = 300) // about 26 KB
        TestAudioFiles.writeWav(File(root, "sounds/long.wav"), durationMs = 3_000) // about 265 KB
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync { player.release() }
        root.deleteRecursively()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test
    fun aShortClipLoadsIntoSoundPoolAndPlays() {
        onMain { player.load("short", "sounds/short.wav") }
        waitFor("the short clip to decode") { player.isReady("short") }

        onMain { player.play("short", volume = 1f) }

        assertEquals(SoundPlayer.PlaybackPath.SOUND_POOL, player.activePath)
    }

    @Test
    fun aLongClipGoesThroughMediaPlayer() {
        onMain { player.load("long", "sounds/long.wav") }
        assertTrue(player.isReady("long"))

        onMain { player.play("long", volume = 1f) }

        assertEquals(SoundPlayer.PlaybackPath.MEDIA_PLAYER, player.activePath)
        waitFor("MediaPlayer to start") { player.isMediaPlayerPlaying }
    }

    @Test
    fun playbackIsExclusiveAcrossBothPaths() {
        onMain {
            player.load("short", "sounds/short.wav")
            player.load("long", "sounds/long.wav")
        }
        waitFor("the short clip to decode") { player.isReady("short") }

        onMain { player.play("long", volume = 1f) }
        waitFor("MediaPlayer to start") { player.isMediaPlayerPlaying }
        onMain { player.play("short", volume = 1f) }

        // The long clip was stopped and released for the short one.
        assertEquals(SoundPlayer.PlaybackPath.SOUND_POOL, player.activePath)
        assertFalse(player.isMediaPlayerPlaying)

        onMain { player.play("long", volume = 1f) }
        assertEquals(SoundPlayer.PlaybackPath.MEDIA_PLAYER, player.activePath)
    }

    @Test
    fun aMissingFileOrAnUnloadedClipPlaysNothing() {
        onMain {
            player.load("missing", "sounds/nope.wav")
            player.load("short", "sounds/short.wav")
        }
        waitFor("the short clip to decode") { player.isReady("short") }
        onMain {
            player.unload("short")
            player.play("missing", volume = 1f)
            player.play("short", volume = 1f)
        }

        assertFalse(player.isReady("missing"))
        assertNull(player.activePath)
    }
}
