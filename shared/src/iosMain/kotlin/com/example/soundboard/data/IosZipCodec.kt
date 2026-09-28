@file:OptIn(ExperimentalForeignApi::class)

package com.example.soundboard.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.MAX_WBITS
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * [ZipCodec] for iOS (#266), which has zlib but no zip API: the archive's structure is read
 * and written here, and zlib inflates deflated entries and checksums new ones. It reads what
 * Android (java.util.zip) and the web (fflate) write, stored or deflated. It writes entries
 * stored, not deflated: the recordings in a backup are compressed audio already, and every
 * zip reader takes stored entries.
 */
class IosZipCodec : ZipCodec {

    override fun read(zip: ByteArray): List<ZipEntryData> {
        val end = endOfCentralDirectory(zip)
        val count = zip.u16(end + 10)
        var at = zip.u32(end + 16).toInt()
        val entries = mutableListOf<ZipEntryData>()
        repeat(count) {
            check(at + 46 <= zip.size && zip.u32(at) == CENTRAL_HEADER) { "not a zip: bad central directory" }
            val method = zip.u16(at + 10)
            val compressedSize = zip.u32(at + 20).toInt()
            val size = zip.u32(at + 24).toInt()
            val nameLength = zip.u16(at + 28)
            val extraLength = zip.u16(at + 30)
            val commentLength = zip.u16(at + 32)
            val localHeader = zip.u32(at + 42).toInt()
            val name = zip.decodeToString(at + 46, at + 46 + nameLength)
            at += 46 + nameLength + extraLength + commentLength

            if (name.endsWith("/")) return@repeat
            check(localHeader + 30 <= zip.size && zip.u32(localHeader) == LOCAL_HEADER) { "not a zip: bad local header" }
            val dataStart = localHeader + 30 + zip.u16(localHeader + 26) + zip.u16(localHeader + 28)
            check(dataStart + compressedSize <= zip.size) { "not a zip: $name runs past the end" }
            val data = zip.copyOfRange(dataStart, dataStart + compressedSize)
            val bytes = when (method) {
                STORED -> data
                DEFLATED -> inflateRaw(data, size)
                else -> error("$name uses compression method $method")
            }
            entries += ZipEntryData(name, bytes)
        }
        return entries
    }

    override fun write(entries: List<ZipEntryData>): ByteArray {
        val out = Bytes()
        val central = Bytes()
        entries.forEach { entry ->
            val name = entry.name.encodeToByteArray()
            val crc = crc32Of(entry.bytes)
            val offset = out.size

            out.u32(LOCAL_HEADER); out.u16(VERSION); out.u16(UTF8_NAMES); out.u16(STORED)
            out.u16(DOS_TIME); out.u16(DOS_DATE); out.u32(crc)
            out.u32(entry.bytes.size.toLong()); out.u32(entry.bytes.size.toLong())
            out.u16(name.size); out.u16(0)
            out.add(name); out.add(entry.bytes)

            central.u32(CENTRAL_HEADER); central.u16(VERSION); central.u16(VERSION); central.u16(UTF8_NAMES); central.u16(STORED)
            central.u16(DOS_TIME); central.u16(DOS_DATE); central.u32(crc)
            central.u32(entry.bytes.size.toLong()); central.u32(entry.bytes.size.toLong())
            central.u16(name.size); central.u16(0); central.u16(0); central.u16(0); central.u16(0)
            central.u32(0); central.u32(offset.toLong())
            central.add(name)
        }
        val centralStart = out.size
        out.add(central.toByteArray())
        out.u32(END_OF_CENTRAL_DIRECTORY); out.u16(0); out.u16(0)
        out.u16(entries.size); out.u16(entries.size)
        out.u32(central.size.toLong()); out.u32(centralStart.toLong()); out.u16(0)
        return out.toByteArray()
    }

    /** Where the end-of-central-directory record starts: the last one, searching back past a comment. */
    private fun endOfCentralDirectory(zip: ByteArray): Int {
        var at = zip.size - 22
        val lowest = maxOf(0, zip.size - 22 - 0xFFFF)
        while (at >= lowest) {
            if (zip.u32(at) == END_OF_CENTRAL_DIRECTORY) return at
            at--
        }
        error("not a zip")
    }

    /** Inflates a raw deflate stream (no zlib header, as zip stores it) into [size] bytes. */
    private fun inflateRaw(data: ByteArray, size: Int): ByteArray {
        val result = ByteArray(size)
        if (size == 0) return result
        val input = if (data.isEmpty()) ByteArray(1) else data
        memScoped {
            val stream = alloc<z_stream>()
            check(inflateInit2_(stream.ptr, -MAX_WBITS, zlibVersion()?.toKString(), sizeOf<z_stream>().toInt()) == Z_OK) { "zlib didn't start" }
            try {
                input.usePinned { inPinned ->
                    result.usePinned { outPinned ->
                        stream.next_in = inPinned.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_in = data.size.toUInt()
                        stream.next_out = outPinned.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_out = size.toUInt()
                        check(inflate(stream.ptr, Z_FINISH) == Z_STREAM_END) { "a zip entry doesn't inflate" }
                    }
                }
            } finally {
                inflateEnd(stream.ptr)
            }
        }
        return result
    }

    private fun crc32Of(bytes: ByteArray): Long {
        if (bytes.isEmpty()) return 0
        return bytes.usePinned { crc32(0uL, it.addressOf(0).reinterpret<UByteVar>(), bytes.size.toUInt()).toLong() }
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50L
        const val CENTRAL_HEADER = 0x02014b50L
        const val END_OF_CENTRAL_DIRECTORY = 0x06054b50L
        const val VERSION = 20
        const val STORED = 0
        const val DEFLATED = 8
        /** General-purpose flag bit 11: names are UTF-8. */
        const val UTF8_NAMES = 0x0800
        /** 1980-01-01 00:00, the earliest time a zip can hold; the app doesn't use entry times. */
        const val DOS_TIME = 0
        const val DOS_DATE = (1 shl 5) or 1
    }
}

private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

/** A growable little-endian byte buffer, for writing zip records. */
private class Bytes {
    private var buffer = ByteArray(1024)
    var size = 0
        private set

    fun add(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun u16(value: Int) {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value shr 8).toByte()
    }

    fun u32(value: Long) {
        u16((value and 0xFFFF).toInt())
        u16(((value shr 16) and 0xFFFF).toInt())
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
}
