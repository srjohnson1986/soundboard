package com.example.soundboard.data

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The browser's real storage: each test store gets its own fresh OPFS folder. */
class OpfsFileStoreTest : FileStoreContractTest() {
    override fun newStore(): FileStore = OpfsFileStore(rootDirectory = "test-${Random.nextLong().toULong()}")
}

class FflateZipCodecTest {
    private val codec = FflateZipCodec()

    @Test
    fun `entries survive a write then read`() {
        val entries = listOf(
            ZipEntryData("board.json", "{}".encodeToByteArray()),
            ZipEntryData("sounds/a.m4a", byteArrayOf(0, 1, 2, -1))
        )

        val read = codec.read(codec.write(entries))

        assertEquals(listOf("board.json", "sounds/a.m4a"), read.map { it.name })
        assertContentEquals(byteArrayOf(0, 1, 2, -1), read[1].bytes)
    }

    @Test
    fun `reads the golden backup`() = GoldenBackup.assertReadBy(codec)

    @Test
    fun `something that isn't a zip can't be read`() {
        assertFails { codec.read("not a zip".encodeToByteArray()) }
    }
}

class LocalStorageKeyValueStoreTest {
    private val store = LocalStorageKeyValueStore("test-${Random.nextLong().toULong()}.")

    @Test
    fun `values read back, and missing ones fall back to the default`() {
        assertFalse(store.getBoolean("flag", default = false))
        assertEquals(7, store.getInt("count", default = 7))

        store.putBoolean("flag", true)
        store.putInt("count", 3)

        assertTrue(store.getBoolean("flag", default = false))
        assertEquals(3, store.getInt("count", default = 7))
    }
}
