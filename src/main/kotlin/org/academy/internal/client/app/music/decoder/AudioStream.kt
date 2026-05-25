package org.academy.internal.client.app.music.decoder

import java.nio.ShortBuffer

interface AudioStream : AutoCloseable {
    val channels: Int
    val sampleRate: Int
    val totalSamples: Long

    fun read(pcmBuffer: ShortBuffer): Int

    fun seek(sampleFrame: Long)

    override fun close()
}