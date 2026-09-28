@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.example.soundboard.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

// iOS's side of the storage interfaces in Storage.kt (#266), next to Android's (AndroidStorage.kt)
// and the web's (WebStorage.kt).

/** [FileStore] on a directory of the app's own, [root] (an absolute path). */
class IosFileStore(private val root: String) : FileStore {
    private val files = NSFileManager.defaultManager

    private fun pathOf(path: String) = "$root/$path"

    override suspend fun exists(path: String): Boolean = files.fileExistsAtPath(pathOf(path))

    override suspend fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(pathOf(path))?.toByteArray()

    override suspend fun write(path: String, bytes: ByteArray) {
        val file = pathOf(path)
        files.createDirectoryAtPath(file.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        check(bytes.toNSData().writeToFile(file, atomically = true)) { "couldn't write $path" }
    }

    override suspend fun delete(path: String) {
        files.removeItemAtPath(pathOf(path), error = null)
    }

    override suspend fun list(directory: String): List<StoredFileInfo> {
        val dir = pathOf(directory)
        val names = files.contentsOfDirectoryAtPath(dir, error = null) ?: return emptyList()
        return names.mapNotNull { name ->
            name as String
            val attributes = files.attributesOfItemAtPath("$dir/$name", error = null) ?: return@mapNotNull null
            if (attributes[NSFileType] == NSFileTypeDirectory) return@mapNotNull null
            StoredFileInfo(
                name = name,
                sizeBytes = (attributes[NSFileSize] as NSNumber).longLongValue,
                lastModifiedMillis = ((attributes[NSFileModificationDate] as NSDate).timeIntervalSince1970 * 1000).toLong()
            )
        }
    }
}

/** [KeyValueStore] on the app's user defaults, with every key under [prefix]. */
class UserDefaultsKeyValueStore(
    private val prefix: String,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {
    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(prefix + key) == null) default else defaults.boolForKey(prefix + key)

    override fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(prefix + key) == null) default else defaults.integerForKey(prefix + key).toInt()

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = prefix + key)

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = prefix + key)

    override fun getString(key: String): String? = defaults.stringForKey(prefix + key)

    override fun putString(key: String, value: String?) =
        if (value == null) defaults.removeObjectForKey(prefix + key) else defaults.setObject(value, forKey = prefix + key)
}

/** The built-in boards copied into the app's bundle, of [names]. */
class IosBundledBoards(private val names: Set<String>) : BundledBoards {
    override fun has(name: String): Boolean = name in names && pathOf(name) != null

    override suspend fun read(name: String): ByteArray =
        pathOf(name)?.let { NSData.dataWithContentsOfFile(it)?.toByteArray() } ?: error("$name isn't bundled")

    private fun pathOf(name: String): String? =
        NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), ofType = name.substringAfterLast('.'))
}

/** A file the user picked in the Files picker, which hands the app a copy at [url]. */
class IosPickedFile(private val url: NSURL) : PickedFile {
    override suspend fun extension(): String = url.pathExtension.orEmpty()

    override suspend fun readBytes(): ByteArray {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            return NSData.dataWithContentsOfURL(url)?.toByteArray() ?: error("can't read $url")
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }
}

/**
 * Saves by writing [fileName] to a temporary file and handing it to [export], which lets the
 * user choose where it goes (the Files picker), much as a browser download does.
 */
class IosSaveTarget(private val fileName: String, private val export: (NSURL) -> Unit) : SaveTarget {
    override suspend fun write(bytes: ByteArray) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName
        check(bytes.toNSData().writeToFile(path, atomically = true)) { "couldn't write $fileName" }
        export(NSURL.fileURLWithPath(path))
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
