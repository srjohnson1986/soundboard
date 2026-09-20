package com.example.soundboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soundboard.ui.BoardScreen
import com.example.soundboard.ui.theme.SoundboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Hoisted here (rather than defaulted inside BoardScreen) so the board's
            // themeMode can reach SoundboardTheme, which wraps BoardScreen from outside.
            val vm: BoardViewModel = viewModel(factory = BoardViewModel.Factory(application))
            val board by vm.board.collectAsStateWithLifecycle()
            SoundboardTheme(themeMode = board.themeMode) {
                BoardScreen(vm = vm)
            }
        }
    }
}
