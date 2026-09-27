package com.example.soundboard.data

import java.io.File

/**
 * [FileStore] over a real directory — on Android, the app's `filesDir`. Calls block on disk
 * I/O, so callers run them off the main thread, as they always have (see `ioDispatcher`).
 */
class FileSystemStore(private val root: File) : FileStore {

    /** The real file behind [path], for Android APIs that need one (SoundPool, MediaRecorder). */
    fun file(path: String): File = File(root, path)

    override suspend fun exists(path: String): Boolean = file(path).isFile

    override suspend fun read(path: String): ByteArray? = file(path).takeIf { it.isFile }?.readBytes()

    override suspend fun write(path: String, bytes: ByteArray) {
        val target = file(path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    override suspend fun delete(path: String) {
        file(path).delete()
    }

    override suspend fun list(directory: String): List<StoredFileInfo> =
        file(directory).listFiles()
            ?.filter { it.isFile }
            ?.map { StoredFileInfo(it.name, it.length(), it.lastModified()) }
            ?: emptyList()
}
