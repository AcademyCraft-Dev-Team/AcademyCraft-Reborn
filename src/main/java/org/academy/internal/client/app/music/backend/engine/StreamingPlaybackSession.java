package org.academy.internal.client.app.music.backend.engine;

import org.academy.internal.client.app.music.backend.decoder.DecoderFactory;
import org.academy.internal.client.app.music.backend.decoder.DecoderThread;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class StreamingPlaybackSession {
    private static final int QUEUE_CAPACITY = 8;

    @Nullable
    private final AlSourcePlayer alPlayer;
    @Nullable
    private final DecoderThread decoderRunnable;
    @Nullable
    private final Thread decoderThread;
    @Nullable
    private final BlockingQueue<ByteBuffer> decodedDataQueue;
    @Nullable
    private final BufferStreamer bufferStreamer;

    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean shutdownDecoder = new AtomicBoolean(false);

    private final int sampleRate;
    private final long totalSamples;

    StreamingPlaybackSession(ByteBuffer audioData, long startFrame, float volume) throws IOException {
        var stream = DecoderFactory.create(audioData);
        if (stream == null) throw new IOException("Failed to create audio stream from provided data.");

        sampleRate = stream.getSampleRate();
        totalSamples = stream.getTotalSamples();

        if (startFrame >= totalSamples) {
            stream.close();
            alPlayer = null;
            decoderRunnable = null;
            decoderThread = null;
            decodedDataQueue = null;
            bufferStreamer = null;
            isPlaying.set(false);
            return;
        }

        alPlayer = new AlSourcePlayer();
        alPlayer.initialize();
        alPlayer.setVolume(volume);

        decodedDataQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        shutdownDecoder.set(false);
        bufferStreamer = new BufferStreamer(alPlayer, decodedDataQueue, stream.getChannels(), startFrame);

        decoderRunnable = new DecoderThread(stream, decodedDataQueue, shutdownDecoder, startFrame);
        decoderThread = new Thread(decoderRunnable, "AC-Music-Decoder");
        decoderThread.setDaemon(true);
        decoderThread.start();

        isPlaying.set(true);
    }

    void seek(long frame) {
        if (decoderRunnable != null && bufferStreamer != null && decodedDataQueue != null) {
            decodedDataQueue.clear();
            bufferStreamer.seek(frame);
            decoderRunnable.seek(frame);
        }
    }

    void destroy() {
        shutdownDecoder.set(true);
        if (decoderThread != null) {
            decoderThread.interrupt();
            try {
                decoderThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (alPlayer != null) alPlayer.destroy();

        if (decodedDataQueue != null) {
            decodedDataQueue.stream()
                    .filter(b -> b != DecoderThread.POISON_PILL)
                    .forEach(MemoryUtil::memFree);
            decodedDataQueue.clear();
        }
        isPlaying.set(false);
        isPaused.set(false);
    }

    void update() {
        if (bufferStreamer != null) {
            bufferStreamer.update();
            if (bufferStreamer.isFinished()) isPlaying.set(false);
        }
    }

    void togglePlayPause() {
        if (!isPlaying.get() || alPlayer == null) return;
        isPaused.set(!isPaused.get());

        if (isPaused.get()) alPlayer.pause();
        else alPlayer.play();

    }

    void setVolume(float value) {
        if (alPlayer != null) alPlayer.setVolume(value);
    }

    float getCurrentTime() {
        return bufferStreamer != null ? bufferStreamer.getCurrentTime(sampleRate) : 0;
    }

    boolean isPlaying() {
        return isPlaying.get();
    }

    boolean isPaused() {
        return isPaused.get();
    }

    boolean isFinished() {
        return !isPlaying.get() && (bufferStreamer == null || bufferStreamer.isFinished());
    }

    float getTotalDuration() {
        return totalSamples > 0 && sampleRate > 0 ? (float) totalSamples / sampleRate : 0f;
    }

    long getTotalSamples() {
        return totalSamples;
    }
}