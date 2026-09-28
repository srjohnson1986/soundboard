package com.example.soundboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.soundboard.audio.AndroidMediaVolume
import com.example.soundboard.audio.AudioRecorder
import com.example.soundboard.audio.SoundPlayer
import com.example.soundboard.audio.TtsSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.appFileStore

/** Builds [BoardViewModel] with the real Android storage, audio and speech behind it. */
class BoardViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BoardViewModel(
            BoardRepository(app),
            SoundPlayer(appFileStore(app)),
            AudioRecorder(app, appFileStore(app)),
            SavedBoardRepository(app),
            TtsSpeaker(app),
            DevicePreferences(app),
            RecentBoardsRepository(app),
            mediaVolume = AndroidMediaVolume(app)
        ) as T
    }
}
