package org.academy.internal.client.app.music.decoder

import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

class ByteBufferInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun read(): Int {
        if (!buffer.hasRemaining()) return -1
        return buffer.get().toInt() and 0xFF
    }

    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        if (!buffer.hasRemaining()) return -1
        val length = min(len, buffer.remaining())
        buffer.get(bytes, off, length)
        return length
    }

    override fun available(): Int {
        return buffer.remaining()
    }
}