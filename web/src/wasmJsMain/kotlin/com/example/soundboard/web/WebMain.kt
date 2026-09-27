package com.example.soundboard.web

import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.soundboard.BoardViewModel
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.FflateZipCodec
import com.example.soundboard.data.LocalStorageKeyValueStore
import com.example.soundboard.data.OpfsFileStore
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.data.WebBundledBoards
import com.example.soundboard.data.requestPersistentStorage
import com.example.soundboard.ui.BoardScreen
import com.example.soundboard.ui.theme.SoundboardTheme
import kotlinx.coroutines.MainScope

/** The built-in boards web/build.gradle.kts copies next to the app. */
private val BUNDLED_BOARDS = setOf("tts-care-board.zip")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    requestPersistentStorage()
    val files = OpfsFileStore()
    val vm = BoardViewModel(
        boardRepo = BoardRepository(files, FflateZipCodec(), WebBundledBoards("boards/", BUNDLED_BOARDS)),
        player = WebAudioPlayer(files, MainScope()),
        recorder = UnavailableRecorder(),
        savedBoardRepo = SavedBoardRepository(files),
        speaker = WebSpeaker(),
        devicePrefs = DevicePreferences(LocalStorageKeyValueStore("soundboard.")),
        recentBoardsRepo = RecentBoardsRepository(files)
    )
    removeLoadingMessage()
    ComposeViewport {
        val board by vm.board.collectAsStateWithLifecycle()
        SoundboardTheme(themeMode = board.themeMode) {
            BoardScreen(vm = vm)
        }
    }
}

private fun removeLoadingMessage(): Unit = js("{ const el = document.getElementById('loading'); if (el) el.remove(); }")
