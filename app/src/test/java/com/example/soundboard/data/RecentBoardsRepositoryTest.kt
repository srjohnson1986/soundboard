package com.example.soundboard.data

import kotlinx.coroutines.test.runTest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentBoardsRepositoryTest {

    private lateinit var repo: RecentBoardsRepository

    @Before
    fun setUp() {
        repo = RecentBoardsRepository(ApplicationProvider.getApplicationContext<android.content.Context>())
    }

    @Test
    fun `recent is empty by default`() = runTest {
        assertTrue(repo.recent().isEmpty())
    }

    @Test
    fun `recordUsed puts the newest entry first`() = runTest {
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.BUILT_IN, assetName = "jeremy-care-board.zip", label = "Jeremy", usedAt = 1L))
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "abc", label = "My Board", usedAt = 2L))

        assertEquals(listOf("My Board", "Jeremy"), repo.recent().map { it.label })
    }

    @Test
    fun `recordUsed on the same preset moves it to the front instead of duplicating it`() = runTest {
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "abc", label = "My Board", usedAt = 1L))
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.BUILT_IN, assetName = "jeremy-care-board.zip", label = "Jeremy", usedAt = 2L))
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "abc", label = "My Board (renamed)", usedAt = 3L))

        val recent = repo.recent()
        assertEquals(2, recent.size)
        assertEquals("My Board (renamed)", recent[0].label)
        assertEquals("Jeremy", recent[1].label)
    }

    @Test
    fun `recent caps at 5 entries`() = runTest {
        repeat(7) { i ->
            repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "id$i", label = "Board $i", usedAt = i.toLong()))
        }

        val recent = repo.recent()
        assertEquals(5, recent.size)
        assertEquals(listOf("Board 6", "Board 5", "Board 4", "Board 3", "Board 2"), recent.map { it.label })
    }

    @Test
    fun `entries written as lowercase kind strings by older builds still read back`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(context.filesDir, "recent_presets.json").writeText(
            """[{"kind":"saved","id":"abc","label":"Mine","usedAt":2},""" +
                """{"kind":"factory","assetName":"jeremy-care-board.zip","label":"Jeremy","usedAt":1}]"""
        )

        assertEquals(listOf(RecentBoardKind.SAVED, RecentBoardKind.BUILT_IN), repo.recent().map { it.kind })
    }
}
