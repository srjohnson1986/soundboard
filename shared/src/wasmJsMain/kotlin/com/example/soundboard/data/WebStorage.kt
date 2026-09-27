package com.example.soundboard.data

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json

// The browser's side of the storage interfaces in Storage.kt, apart from OpfsFileStore.

/** [ZipCodec] on fflate. Reads the zips java.util.zip writes on Android, and vice versa. */
class FflateZipCodec : ZipCodec {

    override fun read(zip: ByteArray): List<ZipEntryData> {
        val files = unzipSync(zip.toUint8Array())
        return Json.decodeFromString<List<String>>(objectKeysJson(files))
            .filterNot { it.endsWith("/") }
            .map { name -> ZipEntryData(name, property(files, name).uint8ArrayToByteArray()) }
    }

    override fun write(entries: List<ZipEntryData>): ByteArray {
        val files = newObject()
        entries.forEach { setProperty(files, it.name, it.bytes.toUint8Array()) }
        return zipSync(files).uint8ArrayToByteArray()
    }
}

/** [KeyValueStore] on the browser's localStorage, with every key under [prefix]. */
class LocalStorageKeyValueStore(private val prefix: String) : KeyValueStore {
    override fun getBoolean(key: String, default: Boolean): Boolean =
        localStorageGet(prefix + key)?.toBooleanStrictOrNull() ?: default

    override fun getInt(key: String, default: Int): Int = localStorageGet(prefix + key)?.toIntOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) = localStorageSet(prefix + key, value.toString())

    override fun putInt(key: String, value: Int) = localStorageSet(prefix + key, value.toString())
}

/** Built-in boards served next to the web app, at [baseUrl] + name, for each of [names]. */
class WebBundledBoards(private val baseUrl: String, private val names: Set<String>) : BundledBoards {
    override fun has(name: String): Boolean = name in names

    override suspend fun read(name: String): ByteArray {
        check(has(name)) { "$name isn't bundled" }
        return fetchBytes(baseUrl + name).await<JsAny>().uint8ArrayToByteArray()
    }
}

private fun newObject(): JsAny = js("({})")

private fun setProperty(target: JsAny, name: String, value: JsAny): Unit = js("{ target[name] = value; }")

private fun property(target: JsAny, name: String): JsAny = js("target[name]")

private fun objectKeysJson(target: JsAny): String = js("JSON.stringify(Object.keys(target))")

// localStorage can throw (blocked storage, private modes); treat that as "nothing stored".
private fun localStorageGet(key: String): String? = js("(() => { try { return localStorage.getItem(key); } catch (e) { return null; } })()")

private fun localStorageSet(key: String, value: String): Unit = js("{ try { localStorage.setItem(key, value); } catch (e) {} }")

private fun fetchBytes(url: String): Promise<JsAny> = js(
    """(async () => {
        const response = await fetch(url);
        if (!response.ok) throw new Error('HTTP ' + response.status + ' for ' + url);
        return new Uint8Array(await response.arrayBuffer());
    })()"""
)
