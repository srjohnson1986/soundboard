package com.example.soundboard.audio

import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin wrapper over SoundPool. Clips are decoded into memory on load, so taps
 * play with no startup delay. Playback is exclusive: starting a clip stops
 * whatever was playing. Best for clips under ~5 seconds.
 */
class SoundPlayer(maxStreams: Int = 8) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = ConcurrentHashMap<String, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()

    /** Stream ID of the clip currently playing, if any. Only one plays at a time. */
    private var activeStreamId: Int? = null

    init {
        // load() is asynchronous; a clip is only safe to play once this fires.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready.add(sampleId)
        }
    }

    fun load(key: String, file: File) {
        if (soundIds.containsKey(key) || !file.exists()) return
        soundIds[key] = pool.load(file.absolutePath, 1)
    }

    fun play(key: String) {
        val id = soundIds[key] ?: return
        if (!ready.contains(id)) return
        activeStreamId?.let { pool.stop(it) }
        activeStreamId = pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun unload(key: String) {
        soundIds.remove(key)?.let { id ->
            pool.unload(id)
            ready.remove(id)
        }
    }

    fun release() {
        pool.release()
        soundIds.clear()
        ready.clear()
        activeStreamId = null
    }
}
