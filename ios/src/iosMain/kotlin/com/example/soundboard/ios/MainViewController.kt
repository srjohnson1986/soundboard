@file:OptIn(ExperimentalForeignApi::class)

package com.example.soundboard.ios

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.example.soundboard.BoardViewModel
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.IosBundledBoards
import com.example.soundboard.data.IosFileStore
import com.example.soundboard.data.IosZipCodec
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.UserDefaultsKeyValueStore
import com.example.soundboard.ui.BoardScreen
import com.example.soundboard.ui.IosHost
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/** The built-in boards the Xcode project copies into the app; like the web, the one that speaks. */
private val BUNDLED_BOARDS = setOf("tts-care-board.zip")

/**
 * The app's one screen, for the Swift side to show (app/Sources/SoundboardApp.swift): the
 * board, on iOS's storage, audio and speech.
 */
fun MainViewController(): UIViewController {
    IosHost.inApp = true
    configureAudioSession()
    val root = appStorageDirectory()
    val files = IosFileStore(root)
    val vm = BoardViewModel(
        boardRepo = BoardRepository(files, IosZipCodec(), IosBundledBoards(BUNDLED_BOARDS)),
        player = IosAudioPlayer(root),
        recorder = IosRecorder(root),
        savedBoardRepo = SavedBoardRepository(files),
        speaker = IosSpeaker(),
        devicePrefs = DevicePreferences(UserDefaultsKeyValueStore("soundboard.")),
        recentBoardsRepo = RecentBoardsRepository(files),
        mediaVolume = IosMediaVolume()
    )
    return ComposeUIViewController {
        val board by vm.board.collectAsState()
        SoundboardTheme(themeMode = board.themeMode) {
            BoardScreen(vm = vm)
        }
    }
}

/** Application Support: private to the app, backed up with the device, never shown in Files. */
private fun appStorageDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null
    )
    return checkNotNull(url?.path) { "no Application Support directory" }
}
