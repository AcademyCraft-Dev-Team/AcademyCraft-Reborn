package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AnimationManager {
    private static final AnimationManager INSTANCE = new AnimationManager();
    private final List<Animator> runningAnimations = new CopyOnWriteArrayList<>();
    private final List<Animator> pendingAdditions = new CopyOnWriteArrayList<>();

    private AnimationManager() {}

    public static AnimationManager getInstance() {
        return INSTANCE;
    }

    void startAnimation(Animator animator) {
        pendingAdditions.add(animator);
    }

    void remove(Animator animator) {
        runningAnimations.remove(animator);
        pendingAdditions.remove(animator);
    }

    public long getCurrentTime() {
        return System.nanoTime() / 1_000_000;
    }

    public void onFrameUpdate() {
        long currentTime = getCurrentTime();

        if (!pendingAdditions.isEmpty()) {
            for (Animator anim : pendingAdditions) {
                anim.startTime = currentTime + anim.getStartDelay();
                anim.onStartInternal();
            }
            runningAnimations.addAll(pendingAdditions);
            pendingAdditions.clear();
        }

        if (runningAnimations.isEmpty()) {
            return;
        }

        List<Animator> toRemove = new ArrayList<>();
        for (Animator anim : runningAnimations) {
            if (anim.tick(currentTime)) {
                toRemove.add(anim);
            }
        }

        if (!toRemove.isEmpty()) {
            runningAnimations.removeAll(toRemove);
        }
    }
}