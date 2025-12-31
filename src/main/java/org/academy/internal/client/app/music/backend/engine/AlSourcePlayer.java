package org.academy.internal.client.app.music.backend.engine;

import org.lwjgl.openal.AL11;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class AlSourcePlayer {
    private static final int BUFFER_COUNT = 4;
    private int alSource = -1;
    private final int[] alBuffers = new int[BUFFER_COUNT];
    private float volume = 1.0f;

    public void initialize() {
        if (isValid()) return;
        alSource = AL11.alGenSources();
        AL11.alGenBuffers(alBuffers);
        AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
    }

    public void destroy() {
        if (!isValid()) return;
        stop();
        AL11.alDeleteBuffers(alBuffers);
        AL11.alDeleteSources(alSource);
        alSource = -1;
        Arrays.fill(alBuffers, 0);
    }

    public void play() {
        if (isValid()) AL11.alSourcePlay(alSource);
    }

    public void pause() {
        if (isValid()) AL11.alSourcePause(alSource);
    }

    public void stop() {
        if (isValid()) {
            AL11.alSourceStop(alSource);
            resetBufferQueue();
        }
    }

    public void resetBufferQueue() {
        if (isValid()) AL11.alSourcei(alSource, AL11.AL_BUFFER, 0);
    }

    public void queueBuffer(int bufferId, int format, ByteBuffer data, int sampleRate) {
        if (isValid()) {
            AL11.alBufferData(bufferId, format, data, sampleRate);
            AL11.alSourceQueueBuffers(alSource, bufferId);
        }
    }

    public int unqueueSingleProcessedBuffer() {
        if (!isValid() || getProcessedBufferCount() == 0) return 0;
        return AL11.alSourceUnqueueBuffers(alSource);
    }

    public int getProcessedBufferCount() {
        return isValid() ? AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_PROCESSED) : 0;
    }

    public int getQueuedBufferCount() {
        return isValid() ? AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED) : 0;
    }

    public int getBufferSize(int bufferId) {
        return isValid() ? AL11.alGetBufferi(bufferId, AL11.AL_SIZE) : 0;
    }

    public float getCurrentTimeOffset() {
        return isValid() ? AL11.alGetSourcef(alSource, AL11.AL_SEC_OFFSET) : 0;
    }

    public int[] getBufferIds() {
        return alBuffers;
    }

    public void setVolume(float value) {
        volume = value;
        if (isValid()) AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
    }

    public boolean isValid() {
        return alSource != -1 && AL11.alIsSource(alSource);
    }
}