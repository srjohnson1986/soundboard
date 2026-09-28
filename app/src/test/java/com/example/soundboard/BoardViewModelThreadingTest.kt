package com.example.soundboard

import androidx.test.core.app.ApplicationProvider
import com.example.soundboard.audio.FakePlayer
import com.example.soundboard.audio.FakeRecorder
import com.example.soundboard.audio.FakeSpeaker
import com.example.soundboard.data.BoardRepository
import com.example.soundboard.data.DevicePreferences
import com.example.soundboard.data.RecentBoardsRepository
import com.example.soundboard.data.SavedBoardRepository
import com.example.soundboard.model.Board
import com.example.soundboard.model.Page
import com.example.soundboard.model.RowHeight
import com.example.soundboard.model.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [BoardViewModel] on a real thread pool and real files, which only the JVM has. Everything
 * else about the view model is tested in the shared module, on every platform.
 */
@RunWith(RobolectricTestRunner::class)
class BoardViewModelThreadingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: android.content.Context = ApplicationProvider.getApplicationContext()
    private val repo = BoardRepository(context)
    private val player = FakePlayer()
    private val recorder = FakeRecorder()
    private val savedBoardRepo = SavedBoardRepository(context)
    private val speaker = FakeSpeaker()
    private val devicePrefs = DevicePreferences(context)
    private val recentBoardsRepo = RecentBoardsRepository(context)

    private fun BoardRepository.saveBlocking(board: Board) = runBlocking { save(board) }
    private fun BoardRepository.loadBlocking(): Board = runBlocking { load() }

    private fun boardWith(vararg tiles: Tile) = Board(
        pages = listOf(Page(rows = 1, columns = tiles.size, tiles = tiles.toList()))
    )

    @Test
    fun `rapid back-to-back commits on a real IO dispatcher leave the latest board on disk`() {
        // Regression for the save race fixed in #144: each commit launches its own save,
        // and on a real thread pool they used to be able to finish out of order.
        repo.saveBlocking(boardWith(Tile(id = "a")))
        val vm = BoardViewModel(repo, player, recorder, savedBoardRepo, speaker, devicePrefs, recentBoardsRepo, ioDispatcher = Dispatchers.IO)
        waitUntil { vm.board.value.currentPage.tiles.any { it.id == "a" } }

        repeat(40) { i ->
            vm.setLandscapeGrid(0, (i % 5) + 1, (i % 7) + 1)
            vm.setRowHeight(RowHeight.entries[i % RowHeight.entries.size])
        }
        val expected = vm.board.value

        waitUntil { BoardRepository(context).loadBlocking() == expected }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("condition not met within ${timeoutMillis}ms", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }
}
