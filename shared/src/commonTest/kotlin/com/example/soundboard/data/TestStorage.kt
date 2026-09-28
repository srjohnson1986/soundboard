package com.example.soundboard.data

/** A [FileStore] held in memory, for tests that don't care where files really live. */
class InMemoryFileStore : FileStore {
    private val files = mutableMapOf<String, ByteArray>()
    private var clock = 0L
    private val modified = mutableMapOf<String, Long>()

    override suspend fun exists(path: String): Boolean = path in files

    /** [write], for setting up a test outside a coroutine. */
    fun put(path: String, bytes: ByteArray) {
        files[path] = bytes.copyOf()
        modified[path] = ++clock
    }

    /** [exists], for checking a test's outcome outside a coroutine. */
    fun has(path: String): Boolean = path in files

    /** The size of the file at [path], or 0 if there's none. */
    fun sizeOf(path: String): Int = files[path]?.size ?: 0

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

/** A [KeyValueStore] held in memory. */
class MapKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
