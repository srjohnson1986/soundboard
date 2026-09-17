package com.example.soundboard.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Wraps SoundPool for short clips and MediaPlayer for long ones. Clips at or
 * under [longClipThresholdBytes] decode into memory via SoundPool for instant,
 * gapless playback; bigger files stream through MediaPlayer instead. Playback
 * is exclusive on both paths: starting a clip stops whatever was playing.
 */
class SoundPlayer(maxStreams: Int = 8, private val longClipThresholdBytes: Long = 300_000L) : Player {

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
    private var activeStreamId: Int? = null

    /** Clips too big to decode into memory; played via MediaPlayer instead. */
    private val longClips = ConcurrentHashMap<String, File>()
    private var activePlayer: MediaPlayer? = null

    init {
        // load() is asynchronous; a clip is only safe to play once this fires.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready.add(sampleId)
        }
    }

    override fun load(key: String, file: File) {
        if (soundIds.containsKey(key) || longClips.containsKey(key) || !file.exists()) return
        if (file.length() > longClipThresholdBytes) {
            longClips[key] = file
        } else {
            soundIds[key] = pool.load(file.absolutePath, 1)
        }
    }

    override fun play(key: String, volume: Float) {
        stopActive()
        val vol = volume.coerceIn(0f, 1f)

        val longFile = longClips[key]
        if (longFile != null) {
            activePlayer = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(longFile.absolutePath)
                    setVolume(vol, vol)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { player ->
                        player.release()
                        if (activePlayer === player) activePlayer = null
                    }
                    prepareAsync()
                }
            }.getOrNull()
            return
        }

        val id = soundIds[key] ?: return
        if (!ready.contains(id)) return
        activeStreamId = pool.play(id, vol, vol, 1, 0, 1f)
    }

    override fun unload(key: String) {
        soundIds.remove(key)?.let { id ->
            pool.unload(id)
            ready.remove(id)
        }
        longClips.remove(key)
    }

    /** Unloads every clip without releasing the underlying SoundPool, for re-import. */
    override fun clear() {
        stopActive()
        soundIds.values.forEach(pool::unload)
        soundIds.clear()
        ready.clear()
        longClips.clear()
    }

    override fun release() {
        stopActive()
        pool.release()
        soundIds.clear()
        ready.clear()
        longClips.clear()
    }

    private fun stopActive() {
        activeStreamId?.let { pool.stop(it) }
        activeStreamId = null
        activePlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        activePlayer = null
    }
}
