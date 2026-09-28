package com.example.soundboard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RecentBoardsRepositoryTest {

    private val files = InMemoryFileStore()
    private val repo = RecentBoardsRepository(files)

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
    fun `recordUsed on the same board moves it to the front instead of duplicating it`() = runTest {
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "abc", label = "My Board", usedAt = 1L))
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.BUILT_IN, assetName = "jeremy-care-board.zip", label = "Jeremy", usedAt = 2L))
        repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "abc", label = "My Board (renamed)", usedAt = 3L))

        assertEquals(listOf("My Board (renamed)", "Jeremy"), repo.recent().map { it.label })
    }

    @Test
    fun `recent caps at 5 entries`() = runTest {
        repeat(7) { i ->
            repo.recordUsed(RecentBoardEntry(kind = RecentBoardKind.SAVED, id = "id$i", label = "Board $i", usedAt = i.toLong()))
        }

        assertEquals(listOf("Board 6", "Board 5", "Board 4", "Board 3", "Board 2"), repo.recent().map { it.label })
    }

    @Test
    fun `entries written as lowercase kind strings by older builds still read back`() = runTest {
        files.put(
            "recent_presets.json",
            ("""[{"kind":"saved","id":"abc","label":"Mine","usedAt":2},""" +
                """{"kind":"factory","assetName":"jeremy-care-board.zip","label":"Jeremy","usedAt":1}]""").encodeToByteArray()
        )

        assertEquals(listOf(RecentBoardKind.SAVED, RecentBoardKind.BUILT_IN), repo.recent().map { it.kind })
    }
}
