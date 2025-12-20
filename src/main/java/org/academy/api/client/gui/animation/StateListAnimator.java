package org.academy.api.client.gui.animation;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StateListAnimator {
    private final List<StateTuple> tuples = new ArrayList<>();
    @Nullable
    private Animator runningAnimator = null;
    @Nullable
    private Animator lastMatch = null;

    public void addState(int stateMask, Animator animator) {
        tuples.add(new StateTuple(stateMask, animator));
    }

    public void setState(int stateMask) {
        Animator match = null;

        for (var tuple : tuples) {
            if ((stateMask & tuple.mask) == tuple.mask) {
                match = tuple.animator;
                break;
            }
        }

        if (match == lastMatch) {
            return;
        }

        if (runningAnimator != null) {
            runningAnimator.cancel();
        }

        lastMatch = match;
        runningAnimator = match;

        if (match != null) {
            match.start();
        }
    }

    public void jumpToCurrentState() {
        if (runningAnimator != null) {
            runningAnimator.end();
        }
    }

    private record StateTuple(int mask, Animator animator) {
    }
}