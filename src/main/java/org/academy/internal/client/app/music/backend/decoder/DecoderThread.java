package org.academy.internal.client.app.music.backend.decoder;

import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class DecoderThread implements Runnable {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    public static final ByteBuffer POISON_PILL = ByteBuffer.allocate(0);

    private final AudioStream stream;
    private final BlockingQueue<ByteBuffer> dataQueue;
    private final AtomicBoolean shutdown;
    private final long startFrame;
    private final AtomicLong pendingSeek = new AtomicLong(-1);

    public DecoderThread(
            AudioStream stream,
            BlockingQueue<ByteBuffer> dataQueue,
            AtomicBoolean shutdown,
            long startFrame
    ) {
        this.stream = stream;
        this.dataQueue = dataQueue;
        this.shutdown = shutdown;
        this.startFrame = startFrame;
    }

    public void seek(long frame) {
        pendingSeek.set(frame);
    }

    @Override
    public void run() {
        ByteBuffer pcmStagingBuffer = null;
        try {
            if (stream.getChannels() <= 0) return;
            performInitialSeek();
            pcmStagingBuffer = allocateStagingBuffer();
            executeDecodingLoop(pcmStagingBuffer);
        } catch (IOException e) {
            LOGGER.error("[DecoderThread] run(): IO error during decoding", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanupResources(pcmStagingBuffer);
        }
    }

    private void performInitialSeek() throws IOException {
        if (startFrame > 0) stream.seek(startFrame);
    }

    private ByteBuffer allocateStagingBuffer() {
        var bufferSizeInFrames = stream.getSampleRate() / 4;
        var pcmBufferSize = bufferSizeInFrames * stream.getChannels() * 2;
        return MemoryUtil.memAlloc(pcmBufferSize);
    }

    private void executeDecodingLoop(ByteBuffer pcmStagingBuffer) throws IOException, InterruptedException {
        while (!shutdown.get()) {
            handlePendingSeek();

            if (Thread.currentThread().isInterrupted()) break;
            if (!processSingleFrame(pcmStagingBuffer)) break;
        }
    }

    private void handlePendingSeek() throws IOException {
        var seekTarget = pendingSeek.getAndSet(-1);
        if (seekTarget != -1) {
            stream.seek(seekTarget);
            dataQueue.clear();
        }
    }

    private boolean processSingleFrame(ByteBuffer pcmStagingBuffer) throws IOException, InterruptedException {
        pcmStagingBuffer.clear();
        var channels = stream.getChannels();
        var samplesRead = readFromStream(pcmStagingBuffer.asShortBuffer(), channels);

        if (samplesRead <= 0) return false;

        enqueueData(pcmStagingBuffer, samplesRead, channels);
        return true;
    }

    private void enqueueData(ByteBuffer pcmStagingBuffer, int samplesRead, int channels) throws InterruptedException {
        pcmStagingBuffer.position(0).limit(samplesRead * channels * 2);

        var dataCopy = MemoryUtil.memAlloc(pcmStagingBuffer.remaining());
        dataCopy.order(ByteOrder.nativeOrder());
        dataCopy.put(pcmStagingBuffer).flip();

        dataQueue.put(dataCopy);
    }

    private void cleanupResources(@Nullable ByteBuffer pcmStagingBuffer) {
        stream.close();
        sendPoisonPill();
        if (pcmStagingBuffer != null) MemoryUtil.memFree(pcmStagingBuffer);
    }

    private void sendPoisonPill() {
        try {
            dataQueue.put(POISON_PILL);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private int readFromStream(ShortBuffer pcmShortBuffer, int channels) throws IOException {
        pcmShortBuffer.clear();
        var framesToRead = pcmShortBuffer.capacity() / channels;
        pcmShortBuffer.limit(framesToRead * channels);
        return stream.read(pcmShortBuffer);
    }
}