package com.example.soundboard.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The media volume, which [SoundPlayer]'s clips and [TtsSpeaker]'s speech both play at. Android
 * records volume changes in its system settings, so watching those catches a change made with
 * the volume keys or from another app; [refresh] catches any it doesn't report.
 */
class AndroidMediaVolume(context: Context) : MediaVolume {
    private val resolver = context.applicationContext.contentResolver
    private val audio = context.getSystemService(AudioManager::class.java)

    private val _muted = MutableStateFlow(isMuted())
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        resolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    override fun refresh() {
        _muted.value = isMuted()
    }

    override fun release() {
        resolver.unregisterContentObserver(observer)
    }

    private fun isMuted(): Boolean =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)
}
