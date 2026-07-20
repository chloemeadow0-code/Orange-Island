package com.orangeisland.app.data

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream

/**
 * Wraps an [InputStream] so that [close] does not propagate to the delegate.
 *
 * Used to hand a single ZIP entry stream to a parser (which conventionally
 * closes its source) without tearing down the underlying
 * [java.util.zip.ZipInputStream] that still needs to advance to the next entry.
 */
internal class NonClosingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
    override fun close() { /* no-op: the caller owns the delegate */ }
}

/**
 * Returns true if [input] begins with the ZIP local-file-header magic ("PK").
 * Consumes nothing: the two probed bytes are restored via mark/reset.
 */
internal fun isZip(input: BufferedInputStream): Boolean {
    input.mark(2)
    val b0 = input.read()
    val b1 = input.read()
    input.reset()
    return b0 == 0x50 && b1 == 0x4B
}

/**
 * Peeks the first non-whitespace character without consuming it (mark/reset),
 * so the underlying parser still sees the full document. Returns null on EOF.
 */
internal fun peekFirstNonWhitespace(input: BufferedInputStream): Char? {
    input.mark(PEEK_LIMIT)
    var c = input.read()
    while (c != -1 && c.toChar().isWhitespace()) c = input.read()
    input.reset()
    return if (c == -1) null else c.toChar()
}

private const val PEEK_LIMIT = 4096
