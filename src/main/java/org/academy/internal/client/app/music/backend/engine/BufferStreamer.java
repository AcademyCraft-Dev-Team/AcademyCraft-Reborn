package org.academy.internal.client.app.music.backend.engine;

import org.academy.internal.client.app.music.backend.decoder.DecoderThread;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

final class BufferStreamer {
    private final AlSourcePlayer alPlayer;
    private final BlockingQueue<ByteBuffer> decodedDataQueue;
    private final int channels;

    private boolean isPrimed = false;
    private volatile boolean streamReadFinished = false;
    private long baseSampleOffset;

    BufferStreamer(AlSourcePlayer alPlayer, BlockingQueue<ByteBuffer> dataQueue, int channels, long startFrame) {
        this.alPlayer = alPlayer;
        decodedDataQueue = dataQueue;
        this.channels = channels;
        baseSampleOffset = startFrame;
    }

    void seek(long frame) {
        alPlayer.stop();
        baseSampleOffset = frame;
        isPrimed = false;
        streamReadFinished = false;
    }

    void update() {
        if (isFinished()) return;

        if (!isPrimed) primeInitialBuffers();
        else streamNextBuffers();
    }

    private void primeInitialBuffers() {
        var canStartPlaying = decodedDataQueue.size() >= alPlayer.getBufferIds().length || (streamReadFinished && !decodedDataQueue.isEmpty());
        if (canStartPlaying) {
            var filledCount = 0;
            for (var bufferId : alPlayer.getBufferIds()) {
                if (feedBuffer(bufferId)) filledCount++;
                else break;
            }
            if (filledCount > 0) {
                alPlayer.play();
                isPrimed = true;
            }
        }
    }

    private void streamNextBuffers() {
        var processedCount = alPlayer.getProcessedBufferCount();
        for (var i = 0; i < processedCount; i++) {
            var bufferId = alPlayer.unqueueSingleProcessedBuffer();
            if (bufferId != 0) {
                updateSampleOffset(bufferId);
                feedBuffer(bufferId);
            }
        }
    }

    private boolean feedBuffer(int bufferId) {
        var pcmData = decodedDataQueue.poll();
        if (pcmData == null) return false;

        if (pcmData == DecoderThread.POISON_PILL) {
            streamReadFinished = true;
            return false;
        }

        var format = channels == 1 ? AL11.AL_FORMAT_MONO16 : AL11.AL_FORMAT_STEREO16;
        var sampleRate = alPlayer.isValid() ? 44100 : 0;
        alPlayer.queueBuffer(bufferId, format, pcmData, sampleRate);
        MemoryUtil.memFree(pcmData);
        return true;
    }

    private void updateSampleOffset(int bufferId) {
        if (channels > 0) {
            var bytesInBuf = alPlayer.getBufferSize(bufferId);
            var framesInBuf = bytesInBuf / (channels * 2);
            baseSampleOffset += framesInBuf;
        }
    }

    float getCurrentTime(int sampleRate) {
        if (sampleRate == 0) return 0;
        var alOffset = alPlayer.getCurrentTimeOffset();
        return ((float) baseSampleOffset / sampleRate) + alOffset;
    }

    boolean isFinished() {
        return streamReadFinished && alPlayer.getQueuedBufferCount() == 0;
    }
}