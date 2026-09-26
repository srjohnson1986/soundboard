package com.example.soundboard.data

/** A [FileStore] held in memory, for tests that don't care where files really live. */
class InMemoryFileStore : FileStore {
    private val files = mutableMapOf<String, ByteArray>()
    private var clock = 0L
    private val modified = mutableMapOf<String, Long>()

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun read(path: String): ByteArray? = files[path]?.copyOf()

    override suspend fun write(path: String, bytes: ByteArray) {
        files[path] = bytes.copyOf()
        modified[path] = ++clock
    }

    override suspend fun delete(path: String) {
        files -= path
        modified -= path
    }

    override suspend fun list(directory: String): List<StoredFileInfo> {
        val prefix = "$directory/"
        return files.filterKeys { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
            .map { (path, bytes) -> StoredFileInfo(path.removePrefix(prefix), bytes.size.toLong(), modified.getValue(path)) }
    }
}

/**
 * Stands in for a real zip format: entries survive a round trip, which is all the
 * repository relies on. The real codecs have their own tests on each platform.
 */
class FakeZipCodec : ZipCodec {
    override fun read(zip: ByteArray): List<ZipEntryData> {
        val text = zip.decodeToString()
        require(text.startsWith(MAGIC)) { "not a fake zip" }
        return text.removePrefix(MAGIC).lines().filter { it.isNotEmpty() }.map { line ->
            val (name, hex) = line.split('\t')
            ZipEntryData(name, hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
    }

    override fun write(entries: List<ZipEntryData>): ByteArray = buildString {
        append(MAGIC)
        entries.forEach { entry ->
            append(entry.name).append('\t')
            entry.bytes.forEach { append((it.toInt() and 0xFF).toString(16).padStart(2, '0')) }
            append('\n')
        }
    }.encodeToByteArray()

    private companion object {
        const val MAGIC = "FAKEZIP\n"
    }
}

class FakePickedFile(private val bytes: ByteArray?, private val extension: String = "") : PickedFile {
    override suspend fun extension(): String = extension
    override suspend fun readBytes(): ByteArray = bytes ?: error("can't be read")
}

class CapturingSaveTarget : SaveTarget {
    var written: ByteArray? = null
        private set

    override suspend fun write(bytes: ByteArray) {
        written = bytes
    }
}

class FakeBundledBoards(private val boards: Map<String, ByteArray> = emptyMap()) : BundledBoards {
    override fun has(name: String): Boolean = name in boards
    override suspend fun read(name: String): ByteArray = boards[name] ?: error("$name isn't bundled")
}
