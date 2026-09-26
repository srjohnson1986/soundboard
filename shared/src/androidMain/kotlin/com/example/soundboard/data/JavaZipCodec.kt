package com.example.soundboard.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** [ZipCodec] on `java.util.zip`, the same format every backup and built-in board has always used. */
class JavaZipCodec : ZipCodec {

    override fun read(zip: ByteArray): List<ZipEntryData> = buildList {
        ZipInputStream(zip.inputStream()).use { input ->
            var entry = input.nextEntry
            // A stream that isn't a zip at all yields no entries rather than failing. Report
            // it, so importing the wrong file says "Import failed" instead of seeming to work.
            if (entry == null) error("not a zip archive")
            while (entry != null) {
                if (!entry.isDirectory) add(ZipEntryData(entry.name, input.readBytes()))
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }

    override fun write(entries: List<ZipEntryData>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.name))
                zip.write(entry.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
