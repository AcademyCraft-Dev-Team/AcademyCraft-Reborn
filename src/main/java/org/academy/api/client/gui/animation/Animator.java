package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;

public abstract class Animator {
    long startTime = -1;
    long startDelay = 0;
    long duration = 300;
    boolean running = false;
    boolean paused = false;
    long pauseBeginTime = -1;
    private final List<AnimatorListener> listeners = new ArrayList<>();

    public void start() {
        if (running) return;
        running = true;
        paused = false;
        pauseBeginTime = -1;
        AnimationManager.startAnimation(this);
    }

    public void cancel() {
        if (!running) return;
        running = false;
        paused = false;
        AnimationManager.remove(this);
        var tempList = new ArrayList<>(listeners);
        for (var listener : tempList) listener.onAnimationCancel(this);
    }

    public void end() {
        if (!running) return;
        running = false;
        paused = false;
        AnimationManager.remove(this);
        var tempList = new ArrayList<>(listeners);
        for (var listener : tempList) listener.onAnimationEnd(this);
    }

    public void pause() {
        if (running && !paused) {
            paused = true;
            pauseBeginTime = -1;
        }
    }

    public void resume() {
        if (running && paused) paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    void onStartInternal() {
        running = true;
        paused = false;
        var tempList = new ArrayList<>(listeners);
        for (var listener : tempList) listener.onAnimationStart(this);
    }

    public boolean isRunning() {
        return running;
    }

    public void addListener(AnimatorListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) {
        listeners.remove(listener);
    }

    public Animator setDuration(long duration) {
        if (duration < 0) throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        this.duration = duration;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    public Animator setStartDelay(long startDelay) {
        if (startDelay < 0) throw new IllegalArgumentException("Animators cannot have negative start delay: " + startDelay);
        this.startDelay = startDelay;
        return this;
    }

    public long getStartDelay() {
        return startDelay;
    }

    abstract boolean tick(long currentTime);
}