package org.academy.internal.client.app.music.backend.engine;

import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class AudioPlayer {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    @Nullable
    private StreamingPlaybackSession currentSession;
    private float volume = 1.0f;

    public void play(ByteBuffer audioData, long startFrame) {
        stop();
        try {
            currentSession = new StreamingPlaybackSession(audioData, startFrame, volume);
        } catch (IOException e) {
            LOGGER.error("Failed to start audio playback session", e);
            stop();
        }
    }

    public void stop() {
        if (currentSession != null) {
            currentSession.destroy();
            currentSession = null;
        }
    }

    public void update() {
        if (currentSession != null) currentSession.update();
    }

    public void togglePlayPause() {
        if (currentSession != null) currentSession.togglePlayPause();
    }

    public void seek(ByteBuffer audioData, float timeRatio) {
        if (currentSession != null) {
            var totalSamples = currentSession.getTotalSamples();
            var targetFrame = (long) (timeRatio * totalSamples);
            currentSession.seek(targetFrame);
        } else play(audioData, 0);
    }

    public void handleContextReset() {
        LOGGER.info("Sound engine reloaded, resetting media player OpenAL resources.");
        stop();
    }

    public void setVolume(float value) {
        volume = value;
        if (currentSession != null) currentSession.setVolume(value);
    }

    public float getCurrentTime() {
        return currentSession != null ? currentSession.getCurrentTime() : 0;
    }

    public boolean isPlaying() {
        return currentSession != null && currentSession.isPlaying();
    }

    public boolean isPaused() {
        return currentSession != null && currentSession.isPaused();
    }

    public boolean isFinished() {
        return currentSession == null || currentSession.isFinished();
    }

    public float getTotalDuration() {
        return currentSession != null ? currentSession.getTotalDuration() : 0f;
    }

    public float getVolume() {
        return volume;
    }
}