package org.academy.api.client.gui.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AnimationManager {
    private static final List<Animator> runningAnimations = new CopyOnWriteArrayList<>();
    private static final List<Animator> pendingAdditions = new CopyOnWriteArrayList<>();

    private AnimationManager() {
    }

    static void startAnimation(Animator animator) {
        pendingAdditions.add(animator);
    }

    static void remove(Animator animator) {
        runningAnimations.remove(animator);
        pendingAdditions.remove(animator);
    }

    public static void onFrameUpdate() {
        var currentTime = System.nanoTime() / 1_000_000;

        if (!pendingAdditions.isEmpty()) {
            for (var anim : pendingAdditions) {
                anim.startTime = currentTime + anim.getStartDelay();
                anim.onStartInternal();
            }
            runningAnimations.addAll(pendingAdditions);
            pendingAdditions.clear();
        }

        if (runningAnimations.isEmpty()) return;

        var toRemove = new ArrayList<Animator>();
        for (var anim : runningAnimations) {
            if (anim.tick(currentTime)) {
                toRemove.add(anim);
            }
        }

        if (!toRemove.isEmpty()) runningAnimations.removeAll(toRemove);
    }
}