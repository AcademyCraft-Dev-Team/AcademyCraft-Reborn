package org.academy.internal.client.app.music.backend.decoder.flac;

import org.academy.internal.client.app.music.backend.decoder.AudioStream;
import org.academy.internal.client.app.music.backend.decoder.mp3.ByteBufferInputStream;
import org.jflac.FLACDecoder;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public final class FlacAudioStream implements AudioStream {
    private final ByteBuffer originalData;
    private FLACDecoder decoder;
    private StreamInfo streamInfo;

    private short @Nullable [] stagingBuffer;
    private int stagingBufferReadOffset = 0;
    private int stagingBufferWriteOffset = 0;

    @Nullable
    private ByteData reusedByteData = null;

    private boolean endOfStream = false;

    public FlacAudioStream(ByteBuffer audioData) throws IOException {
        originalData = audioData;
        initializeDecoder();
    }

    private void initializeDecoder() throws IOException {
        decoder = new FLACDecoder(new ByteBufferInputStream(originalData.duplicate()));

        streamInfo = decoder.readStreamInfo();
        if (streamInfo == null) throw new IOException("Invalid FLAC stream: Metadata not found");

        decoder.readMetadata(streamInfo);

        resetStagingState();
    }

    private void resetStagingState() {
        stagingBuffer = null;
        reusedByteData = null;
        stagingBufferReadOffset = 0;
        stagingBufferWriteOffset = 0;
        endOfStream = false;
    }

    @Override
    public int getChannels() {
        return streamInfo.getChannels();
    }

    @Override
    public int getSampleRate() {
        return streamInfo.getSampleRate();
    }

    @Override
    public long getTotalSamples() {
        return streamInfo.getTotalSamples();
    }

    @Override
    public int read(ShortBuffer pcmBuffer) throws IOException {
        var startPosition = pcmBuffer.position();

        performReadLoop(pcmBuffer);

        var totalWritten = pcmBuffer.position() - startPosition;
        return calculateReadResult(totalWritten);
    }

    private void performReadLoop(ShortBuffer pcmBuffer) throws IOException {
        while (pcmBuffer.hasRemaining()) {
            if (!ensureDataAvailable()) break;
            transferToOutput(pcmBuffer);
        }
    }

    private boolean ensureDataAvailable() throws IOException {
        if (!isStagingBufferEmpty()) return true;
        return fetchNextFrame();
    }

    private boolean isStagingBufferEmpty() {
        return stagingBuffer == null || stagingBufferReadOffset >= stagingBufferWriteOffset;
    }

    private boolean fetchNextFrame() throws IOException {
        if (endOfStream) return false;

        try {
            var frame = decoder.readNextFrame();
            if (frame == null) {
                endOfStream = true;
                return false;
            }

            reusedByteData = decoder.decodeFrame(frame, reusedByteData);
            updateStagingBuffer(reusedByteData);
            return true;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("FLAC decoding error", e);
        }
    }

    private void transferToOutput(ShortBuffer pcmBuffer) {
        if (stagingBuffer == null) return;

        var available = stagingBufferWriteOffset - stagingBufferReadOffset;
        var toCopy = Math.min(pcmBuffer.remaining(), available);

        pcmBuffer.put(stagingBuffer, stagingBufferReadOffset, toCopy);
        stagingBufferReadOffset += toCopy;
    }

    private int calculateReadResult(int totalWritten) {
        if (totalWritten == 0 && endOfStream) return -1;
        return totalWritten / getChannels();
    }

    private void updateStagingBuffer(ByteData byteData) {
        var bytes = byteData.getData();
        var len = byteData.getLen();
        var samplesCount = len / 2;

        prepareStagingArray(samplesCount);
        convertBytesToSamples(bytes, samplesCount);
    }

    private void prepareStagingArray(int samplesCount) {
        if (stagingBuffer == null || stagingBuffer.length < samplesCount) stagingBuffer = new short[samplesCount];
        stagingBufferReadOffset = 0;
        stagingBufferWriteOffset = samplesCount;
    }

    private void convertBytesToSamples(byte[] bytes, int samplesCount) {
        if (stagingBuffer == null) return;

        for (var i = 0; i < samplesCount; i++) {
            var low = bytes[i * 2] & 0xFF;
            var high = (int) bytes[i * 2 + 1];
            stagingBuffer[i] = (short) ((high << 8) | low);
        }
    }

    @Override
    public void seek(long sampleFrame) throws IOException {
        initializeDecoder();

        var currentFrame = 0L;
        while (currentFrame < sampleFrame && !endOfStream) {
            var frame = decoder.readNextFrame();
            if (frame == null) {
                endOfStream = true;
                break;
            }

            var blockSize = frame.header.blockSize;

            if (currentFrame + blockSize <= sampleFrame) currentFrame += blockSize;
            else {
                reusedByteData = decoder.decodeFrame(frame, reusedByteData);
                updateStagingBuffer(reusedByteData);

                var framesToSkip = sampleFrame - currentFrame;
                stagingBufferReadOffset = (int) (framesToSkip * getChannels());
                currentFrame += framesToSkip;
            }
        }
    }

    @Override
    public void close() {
    }
}