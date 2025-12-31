package org.academy.internal.client.app.music.backend.decoder.mp3;

import javazoom.jl.decoder.*;
import org.academy.AcademyCraft;
import org.academy.internal.client.app.music.backend.decoder.AudioStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public final class Mp3AudioStream implements AudioStream {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private final ByteBuffer originalData;
    private final int channels;
    private final int sampleRate;
    private final long totalSamples;

    @Nullable
    private Bitstream bitstream;
    @Nullable
    private Decoder decoder;

    @Nullable
    private SampleBuffer currentBuffer;
    private int bufferOffset;

    public Mp3AudioStream(ByteBuffer audioData) throws IOException {
        originalData = audioData;

        var header = readFirstFrame(audioData);
        channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
        sampleRate = header.frequency();

        var frameCount = header.maxNumberOfFrames(audioData.remaining());
        totalSamples = frameCount * (long) getFrameSampleCount(header);

        resetState();
    }

    private static Header readFirstFrame(ByteBuffer audioData) throws IOException {
        try (var tempStream = new ByteBufferInputStream(audioData.duplicate())) {
            var scanBs = new Bitstream(tempStream);
            var header = scanBs.readFrame();
            scanBs.close();
            if (header == null) throw new IOException("Empty MP3 stream");
            return header;
        } catch (BitstreamException e) {
            throw new IOException("Failed to read MP3 header", e);
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
    public int read(ShortBuffer dest) {
        if (bitstream == null || decoder == null) return -1;

        var startPos = dest.position();
        while (
                dest.hasRemaining()
                        && (
                        currentBuffer != null
                                && bufferOffset < currentBuffer.getBufferLength()
                                || decodeNextFrame(bitstream, decoder)
                )
        ) transferTo(dest, currentBuffer);

        var totalWritten = dest.position() - startPos;
        return totalWritten > 0 ? totalWritten / channels : -1;
    }

    private boolean decodeNextFrame(Bitstream bitstream, Decoder decoder) {
        try {
            var header = bitstream.readFrame();
            if (header == null) return false;

            currentBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            bufferOffset = 0;
            bitstream.closeFrame();
            return true;
        } catch (Exception e) {
            LOGGER.error("MP3 decode error", e);
            return false;
        }
    }

    private void transferTo(ShortBuffer dest, SampleBuffer currentBuffer) {
        var available = currentBuffer.getBufferLength() - bufferOffset;
        var toCopy = Math.min(dest.remaining(), available);
        dest.put(currentBuffer.getBuffer(), bufferOffset, toCopy);
        bufferOffset += toCopy;
    }

    @Override
    public void seek(long targetSample) throws IOException {
        resetState();
        var bs = bitstream;
        var dec = decoder;

        if (bs == null || dec == null) return;

        try {
            advanceToSample(bs, dec, targetSample);
        } catch (BitstreamException | DecoderException e) {
            throw new IOException("Failed to seek in MP3 stream", e);
        }
    }

    private void advanceToSample(Bitstream bs, Decoder dec, long targetSample) throws BitstreamException, DecoderException {
        long currentSample = 0;
        while (currentSample < targetSample) {
            var header = bs.readFrame();
            if (header == null) break;

            var frameLen = getFrameSampleCount(header);
            if (currentSample + frameLen > targetSample) {
                currentBuffer = (SampleBuffer) dec.decodeFrame(header, bs);
                bufferOffset = (int) (targetSample - currentSample);
                bs.closeFrame();
                break;
            }

            currentSample += frameLen;
            bs.closeFrame();
        }
    }

    private void resetState() {
        closeBitstream();
        decoder = new Decoder();
        bitstream = new Bitstream(new ByteBufferInputStream(originalData.duplicate()));
        currentBuffer = null;
        bufferOffset = 0;
    }

    private void closeBitstream() {
        if (bitstream != null) {
            try {
                bitstream.close();
            } catch (BitstreamException e) {
                LOGGER.error("Error closing bitstream", e);
            }
            bitstream = null;
        }
    }

    private static int getFrameSampleCount(Header h) {
        return h.layer() == 1 ? 384 : (h.layer() == 2 ? 1152 : (h.version() == Header.MPEG1 ? 1152 : 576));
    }

    @Override
    public void close() {
        closeBitstream();
        decoder = null;
    }
}