package com.orangeisland.app.util

import java.io.RandomAccessFile
import java.io.File

/**
 * Result of reading TTF/OTF name-table metadata.
 * @param family Font Family name (nameID 1 or 16)
 * @param subfamily Font Subfamily name (nameID 2 or 17), e.g. "Regular", "Bold"
 */
data class FontMetadata(val family: String, val subfamily: String?)

/**
 * Reads font family + subfamily from a TTF/OTF file via the name table.
 * Works on all API levels without Android SDK dependencies.
 */
fun readFontMeta(file: File): FontMetadata {
    val names = parseTtfNameTable(file)
    val family = names[1] ?: names[16] ?: file.nameWithoutExtension
    val subfamily = names[2] ?: names[17]
    return FontMetadata(family, subfamily)
}

/**
 * Reads just the font family name from a TTF/OTF file.
 */
fun readFontName(file: File): String = readFontMeta(file).family

/**
 * Parses all name records from the TTF/OTF 'name' table.
 * Returns a map of nameID → best available string (prefers Windows platform).
 */
private fun parseTtfNameTable(file: File): Map<Int, String> {
    return try {
        RandomAccessFile(file, "r").use { raf ->
            // SfVersion: 0x00010000 for TrueType, 0x4F54544F ("OTTO") for OpenType
            val sfVersion = raf.readInt()
            val numTables = (raf.readUnsignedShort())
            raf.skipBytes(6) // searchRange, entrySelector, rangeShift

            var nameOffset = 0L
            var nameLength = 0L

            for (i in 0 until numTables) {
                val tagBytes = ByteArray(4); raf.readFully(tagBytes)
                val tag = String(tagBytes, Charsets.US_ASCII)
                raf.skipBytes(4) // checkSum
                val offset = raf.readUInt32()
                val length = raf.readUInt32()
                if (tag == "name") {
                    nameOffset = offset
                    nameLength = length
                    break
                }
            }
            if (nameLength == 0L) return emptyMap()

            // Parse the 'name' table
            raf.seek(nameOffset)
            val format = raf.readUnsignedShort()
            val count = raf.readUnsignedShort()
            val stringOffset = nameOffset + raf.readUnsignedShort()

            // nameID → (platformId, value); prefer Windows (3) over Mac (1)
            val results = mutableMapOf<Int, Pair<Int, String>>()

            for (i in 0 until count) {
                val platformId = raf.readUnsignedShort()
                val encodingId = raf.readUnsignedShort()
                val languageId = raf.readUnsignedShort()
                val nameId = raf.readUnsignedShort()
                val length = raf.readUnsignedShort()
                val offset = raf.readUnsignedShort()

                val stringPos = stringOffset + offset
                val savedPos = raf.filePointer
                val bytes = ByteArray(length)
                raf.seek(stringPos)
                raf.readFully(bytes)

                val value: String? = when {
                    platformId == 3 && (encodingId == 1 || encodingId == 10) -> decodeUtf16BE(bytes)
                    platformId == 1 && encodingId == 0 -> decodeMacRoman(bytes)
                    else -> null
                }

                if (value != null && !value.startsWith(".") && value.isNotBlank()) {
                    val existing = results[nameId]
                    if (existing == null || platformId == 3) {
                        results[nameId] = platformId to value
                    }
                }

                raf.seek(savedPos)
            }
            results.mapValues { it.value.second }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun decodeUtf16BE(bytes: ByteArray): String {
    if (bytes.size < 2) return ""
    return try {
        String(bytes, Charsets.UTF_16BE).trim()
    } catch (_: Exception) {
        bytes.filter { it != 0.toByte() }.toByteArray().let { String(it, Charsets.US_ASCII) }.trim()
    }
}

private fun decodeMacRoman(bytes: ByteArray): String {
    return try {
        String(bytes, charset("x-mac-roman")).trim()
    } catch (_: Exception) {
        String(bytes, Charsets.US_ASCII).trim()
    }
}

private fun RandomAccessFile.readUInt16(): Int {
    return (readUnsignedByte() shl 8) or readUnsignedByte()
}

private fun RandomAccessFile.readUInt32(): Long {
    return ((readUInt16().toLong() shl 16) or readUInt16().toLong())
}
