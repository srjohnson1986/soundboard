package com.example.soundboard.data

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [FileStore] on the browser's origin-private file system (OPFS): real files, private to
 * this site, that survive reloads. The browser may clear them under storage pressure unless
 * the site has persistent storage (see [requestPersistentStorage]); backups are the safe copy.
 *
 * [rootDirectory], when set, keeps everything under that folder (tests use a fresh one each).
 */
class OpfsFileStore(private val rootDirectory: String? = null) : FileStore {

    private fun full(path: String) = if (rootDirectory == null) path else "$rootDirectory/$path"

    override suspend fun exists(path: String): Boolean = opfsExists(full(path)).await<JsBoolean>().toBoolean()

    override suspend fun read(path: String): ByteArray? = opfsRead(full(path)).await<JsAny?>()?.uint8ArrayToByteArray()

    override suspend fun write(path: String, bytes: ByteArray) {
        opfsWrite(full(path), bytes.toUint8Array()).await<JsAny?>()
    }

    override suspend fun delete(path: String) {
        opfsDelete(full(path)).await<JsAny?>()
    }

    override suspend fun list(directory: String): List<StoredFileInfo> =
        json.decodeFromString<List<ListedFile>>(opfsList(full(directory)).await<JsString>().toString())
            .map { StoredFileInfo(it.name, it.sizeBytes, it.lastModifiedMillis) }

    @Serializable
    private class ListedFile(val name: String, val sizeBytes: Long, val lastModifiedMillis: Long)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Asks the browser not to clear this site's storage when space runs low; it may say no. */
fun requestPersistentStorage(): Unit = js("{ if (navigator.storage && navigator.storage.persist) navigator.storage.persist(); }")

// Each helper walks the path's folders from the OPFS root. A missing folder or file is
// NotFoundError; a name that's a folder where a file was expected is TypeMismatchError.

private fun opfsExists(path: String): Promise<JsBoolean> = js(
    """(async () => {
        const parts = path.split('/');
        const name = parts.pop();
        let dir = await navigator.storage.getDirectory();
        try {
            for (const part of parts) dir = await dir.getDirectoryHandle(part);
            await dir.getFileHandle(name);
            return true;
        } catch (e) {
            if (e.name === 'NotFoundError' || e.name === 'TypeMismatchError') return false;
            throw e;
        }
    })()"""
)

private fun opfsRead(path: String): Promise<JsAny?> = js(
    """(async () => {
        const parts = path.split('/');
        const name = parts.pop();
        let dir = await navigator.storage.getDirectory();
        try {
            for (const part of parts) dir = await dir.getDirectoryHandle(part);
            const file = await (await dir.getFileHandle(name)).getFile();
            return new Uint8Array(await file.arrayBuffer());
        } catch (e) {
            if (e.name === 'NotFoundError' || e.name === 'TypeMismatchError') return null;
            throw e;
        }
    })()"""
)

private fun opfsWrite(path: String, bytes: JsAny): Promise<JsAny?> = js(
    """(async () => {
        const parts = path.split('/');
        const name = parts.pop();
        let dir = await navigator.storage.getDirectory();
        for (const part of parts) dir = await dir.getDirectoryHandle(part, { create: true });
        const writable = await (await dir.getFileHandle(name, { create: true })).createWritable();
        await writable.write(bytes);
        await writable.close();
        return null;
    })()"""
)

private fun opfsDelete(path: String): Promise<JsAny?> = js(
    """(async () => {
        const parts = path.split('/');
        const name = parts.pop();
        let dir = await navigator.storage.getDirectory();
        try {
            for (const part of parts) dir = await dir.getDirectoryHandle(part);
            await dir.removeEntry(name);
        } catch (e) {
            if (e.name !== 'NotFoundError' && e.name !== 'TypeMismatchError') throw e;
        }
        return null;
    })()"""
)

private fun opfsList(directory: String): Promise<JsString> = js(
    """(async () => {
        let dir = await navigator.storage.getDirectory();
        try {
            for (const part of directory.split('/')) dir = await dir.getDirectoryHandle(part);
        } catch (e) {
            if (e.name === 'NotFoundError' || e.name === 'TypeMismatchError') return '[]';
            throw e;
        }
        const files = [];
        for await (const [name, handle] of dir.entries()) {
            if (handle.kind !== 'file') continue;
            const file = await handle.getFile();
            files.push({ name: name, sizeBytes: file.size, lastModifiedMillis: file.lastModified });
        }
        return JSON.stringify(files);
    })()"""
)
