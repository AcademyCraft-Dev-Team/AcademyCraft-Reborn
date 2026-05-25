package org.academy.internal.client.app.music.decoder.ogg

import org.academy.internal.client.app.music.decoder.AudioStream
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ShortBuffer

class VorbisAudioStream(audioData: ByteBuffer) : AudioStream {
    private val handle: Long
    private val info: STBVorbisInfo
    override val channels: Int
    override val sampleRate: Int
    override val totalSamples: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val error = stack.mallocInt(1)
            handle = STBVorbis.stb_vorbis_open_memory(audioData, error, null)
            if (handle == 0L) throw IOException("Failed to open Ogg Vorbis stream, error: ${error.get(0)}")
            info = STBVorbisInfo.malloc()
            STBVorbis.stb_vorbis_get_info(handle, info)

            channels = info.channels()
            sampleRate = info.sample_rate()
            totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle).toLong()
        }
    }

    override fun read(pcmBuffer: ShortBuffer): Int =
        STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcmBuffer)

    override fun seek(sampleFrame: Long) {
        STBVorbis.stb_vorbis_seek_frame(handle, sampleFrame.toInt())
    }

    override fun close() {
        info.free()
        if (handle != 0L) {
            STBVorbis.stb_vorbis_close(handle)
        }
    }
}