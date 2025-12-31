package org.academy.internal.client.app.music.backend.decoder.ogg;

import org.academy.internal.client.app.music.backend.decoder.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public final class VorbisAudioStream implements AudioStream {
    private final long handle;
    private final STBVorbisInfo info;
    private final int channels;
    private final int sampleRate;
    private final long totalSamples;

    public VorbisAudioStream(ByteBuffer audioData) throws IOException {
        try (var stack = MemoryStack.stackPush()) {
            var error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(audioData, error, null);
            if (handle == 0) throw new IOException("Failed to open Ogg Vorbis stream, error: " + error.get(0));
            info = STBVorbisInfo.malloc();
            STBVorbis.stb_vorbis_get_info(handle, info);

            channels = info.channels();
            sampleRate = info.sample_rate();
            totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
        }
    }

    @Override
    public int getChannels() {
        return channels;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public long getTotalSamples() {
        return totalSamples;
    }

    @Override
    public int read(ShortBuffer pcmBuffer) {
        return STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcmBuffer);
    }

    @Override
    public void seek(long sampleFrame) {
        STBVorbis.stb_vorbis_seek_frame(handle, (int) sampleFrame);
    }

    @Override
    public void close() {
        info.free();
        if (handle != 0) STBVorbis.stb_vorbis_close(handle);
    }
}