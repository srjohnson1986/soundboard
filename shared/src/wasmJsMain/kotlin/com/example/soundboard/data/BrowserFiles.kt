package com.example.soundboard.data

import kotlin.js.Promise
import kotlinx.coroutines.await

/** A file the user picked with the browser's file chooser (a JavaScript `File`). */
class BrowserPickedFile(private val file: JsAny) : PickedFile {
    override suspend fun extension(): String = fileName(file).substringAfterLast('.', "")

    override suspend fun readBytes(): ByteArray = fileBytes(file).await<JsAny>().uint8ArrayToByteArray()
}

/** Saves by downloading: the browser puts the file in its downloads, named [fileName]. */
class DownloadSaveTarget(private val fileName: String, private val mimeType: String) : SaveTarget {
    override suspend fun write(bytes: ByteArray) = download(bytes.toUint8Array(), fileName, mimeType)
}

/** Opens the browser's file chooser for [accept], a list of MIME types; [onPicked] gets the chosen `File`. */
internal fun openFileChooser(accept: String, onPicked: (JsAny) -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.onchange = () => { if (input.files.length > 0) onPicked(input.files[0]); };
        input.click();
    }"""
)

private fun fileName(file: JsAny): String = js("file.name")

private fun fileBytes(file: JsAny): Promise<JsAny> = js("file.arrayBuffer().then(buffer => new Uint8Array(buffer))")

private fun download(bytes: JsAny, fileName: String, mimeType: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }"""
)
