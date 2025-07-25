package org.academy.api.client.gui.animation;

public interface AnimatorListener {
    default void onAnimationStart(Animator animation) {
    }

    default void onAnimationEnd(Animator animation) {
    }

    default void onAnimationCancel(Animator animation) {
    }
}