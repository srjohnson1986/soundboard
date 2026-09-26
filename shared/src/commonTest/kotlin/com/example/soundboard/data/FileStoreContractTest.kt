package com.example.soundboard.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * What every [FileStore] must do, whatever it's backed by. Each platform's real store
 * gets a subclass, so Android and the web can't quietly disagree about the basics.
 */
abstract class FileStoreContractTest {

    abstract fun newStore(): FileStore

    @Test
    fun `a written file reads back the same bytes`() = runTest {
        val store = newStore()

        store.write("sounds/a.m4a", byteArrayOf(1, 2, 3))

        assertTrue(store.exists("sounds/a.m4a"))
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("sounds/a.m4a"))
    }

    @Test
    fun `writing again replaces the content`() = runTest {
        val store = newStore()
        store.write("board.json", "old".encodeToByteArray())

        store.writeText("board.json", "new")

        assertEquals("new", store.readText("board.json"))
    }

    @Test
    fun `a missing file doesn't exist and reads as null`() = runTest {
        val store = newStore()

        assertFalse(store.exists("nothing.json"))
        assertNull(store.read("nothing.json"))
    }

    @Test
    fun `delete removes the file and ignores one that isn't there`() = runTest {
        val store = newStore()
        store.write("sounds/a.m4a", byteArrayOf(1))

        store.delete("sounds/a.m4a")
        store.delete("sounds/never-existed.m4a")

        assertFalse(store.exists("sounds/a.m4a"))
    }

    @Test
    fun `list returns only the files directly inside the directory, with their sizes`() = runTest {
        val store = newStore()
        store.write("sounds/a.m4a", byteArrayOf(1, 2))
        store.write("sounds/b.wav", byteArrayOf(1, 2, 3))
        store.write("presets/x.json", byteArrayOf(1))
        store.write("board.json", byteArrayOf(1))

        val listed = store.list("sounds").associate { it.name to it.sizeBytes }

        assertEquals(mapOf("a.m4a" to 2L, "b.wav" to 3L), listed)
    }

    @Test
    fun `listing a directory that doesn't exist is empty`() = runTest {
        assertTrue(newStore().list("backgrounds").isEmpty())
    }
}

class InMemoryFileStoreTest : FileStoreContractTest() {
    override fun newStore(): FileStore = InMemoryFileStore()
}
