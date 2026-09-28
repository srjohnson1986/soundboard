@file:OptIn(ExperimentalForeignApi::class)

package com.example.soundboard.data

import kotlin.test.AfterTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

class IosFileStoreTest : FileStoreContractTest() {
    override fun newStore(): FileStore = IosFileStore(NSTemporaryDirectory().trimEnd('/') + "/filestore-" + NSUUID().UUIDString)

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), error = null)
            ?.map { it as String }
            ?.filter { it.startsWith("filestore-") }
            ?.forEach { NSFileManager.defaultManager.removeItemAtPath(NSTemporaryDirectory().trimEnd('/') + "/" + it, error = null) }
    }
}

class IosZipCodecTest {
    private val codec = IosZipCodec()

    @Test
    fun `entries survive a write then read`() {
        val entries = listOf(
            ZipEntryData("board.json", "{}".encodeToByteArray()),
            ZipEntryData("sounds/a.m4a", byteArrayOf(0, 1, 2, -1)),
            ZipEntryData("sounds/empty.m4a", ByteArray(0)),
            ZipEntryData("sounds/café.m4a", byteArrayOf(9))
        )

        val read = codec.read(codec.write(entries))

        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.zip(read).forEach { (written, back) -> assertContentEquals(written.bytes, back.bytes) }
    }

    @Test
    fun `a written entry carries the CRC-32 other zip readers check`() {
        val zip = codec.write(listOf(ZipEntryData("hello.txt", "hello".encodeToByteArray())))

        // The local header's CRC-32 field, little-endian: crc32("hello") is 0x3610A686.
        assertContentEquals(byteArrayOf(0x86.toByte(), 0xA6.toByte(), 0x10, 0x36), zip.copyOfRange(14, 18))
    }

    @Test
    fun `reads the golden backup`() = GoldenBackup.assertReadBy(codec)

    @Test
    fun `something that isn't a zip can't be read`() {
        assertFailsWith<IllegalStateException> { codec.read("not a zip".encodeToByteArray()) }
    }
}

class UserDefaultsKeyValueStoreTest {
    private val prefix = "test-" + NSUUID().UUIDString + "."
    private val store = UserDefaultsKeyValueStore(prefix)

    @AfterTest
    fun cleanUp() {
        listOf("on", "count", "voice").forEach { NSUserDefaults.standardUserDefaults.removeObjectForKey(prefix + it) }
    }

    @Test
    fun `values read back and a missing one gives the default`() {
        assertFalse(store.getBoolean("on", false))
        assertEquals(3, store.getInt("count", 3))
        assertNull(store.getString("voice"))

        store.putBoolean("on", true)
        store.putInt("count", 7)
        store.putString("voice", "com.apple.voice.compact.en-US.Samantha")

        val again = UserDefaultsKeyValueStore(prefix)
        assertTrue(again.getBoolean("on", false))
        assertEquals(7, again.getInt("count", 0))
        assertEquals("com.apple.voice.compact.en-US.Samantha", again.getString("voice"))
    }

    @Test
    fun `storing a null string removes it`() {
        store.putString("voice", "some voice")
        store.putString("voice", null)

        assertNull(store.getString("voice"))
    }
}
