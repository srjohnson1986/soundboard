package com.example.soundboard.data

// Kotlin/Wasm can't hand a ByteArray to JavaScript directly (they live in different
// memories), so bytes cross one at a time. That's fast enough for this app's files — sound
// clips and backups of a few MB — and keeps every conversion in one place.

internal fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun uint8Length(array: JsAny): Int = js("array.length")

private fun uint8Get(array: JsAny, index: Int): Int = js("array[index]")

private fun uint8Set(array: JsAny, index: Int, value: Int): Unit = js("{ array[index] = value; }")

/** A copy of these bytes as a JavaScript `Uint8Array`. */
fun ByteArray.toUint8Array(): JsAny {
    val array = newUint8Array(size)
    for (i in indices) uint8Set(array, i, this[i].toInt())
    return array
}

/** A copy of a JavaScript `Uint8Array`'s bytes. */
fun JsAny.uint8ArrayToByteArray(): ByteArray = ByteArray(uint8Length(this)) { uint8Get(this, it).toByte() }
