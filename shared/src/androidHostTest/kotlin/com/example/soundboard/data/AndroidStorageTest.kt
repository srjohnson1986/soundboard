package com.example.soundboard.data

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileSystemStoreTest : FileStoreContractTest() {
    private val roots = mutableListOf<File>()

    override fun newStore(): FileStore = FileSystemStore(createTempDirectory("filestore").toFile().also { roots += it })

    @AfterTest
    fun cleanUp() {
        roots.forEach { it.deleteRecursively() }
    }
}

class JavaZipCodecTest {
    private val codec = JavaZipCodec()

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
    fun `reads every built-in board shipped in presets`() {
        val presets = File("../presets").listFiles { file -> file.extension == "zip" }.orEmpty()
        check(presets.isNotEmpty()) { "expected zips in ${File("../presets").absolutePath}" }

        presets.forEach { zip ->
            val names = codec.read(zip.readBytes()).map { it.name }
            assertTrue("board.json" in names, "${zip.name} has no board.json")
        }
    }

    @Test
    fun `something that isn't a zip can't be read`() {
        assertFailsWith<IllegalStateException> { codec.read("not a zip".encodeToByteArray()) }
    }
}
