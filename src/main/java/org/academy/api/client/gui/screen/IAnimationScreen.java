package org.academy.api.client.gui.screen;

import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IAnimationScreen {
    List<Animator> getScreenAnimations();

    Map<Widget, List<Animator>> getTrackedAnimations();

    default void playAnimation(Animator animator) {
        getScreenAnimations().add(animator);
        animator.start();
    }

    default void playTrackedAnimation(Widget widget, Animator animator) {
        playAnimation(animator);
        getTrackedAnimations().computeIfAbsent(widget, k -> new ArrayList<>()).add(animator);
    }

    default void cancelAnimations(Widget widget) {
        if (getTrackedAnimations().containsKey(widget)) {
            var animators = new ArrayList<>(getTrackedAnimations().get(widget));
            for (var anim : animators) {
                anim.cancel();
                getScreenAnimations().remove(anim);
            }
            getTrackedAnimations().get(widget).clear();
        }
    }

    default void cancelAllAnimations() {
        var animators = new ArrayList<>(getScreenAnimations());
        for (var anim : animators) {
            anim.cancel();
        }
        getScreenAnimations().clear();
        getTrackedAnimations().clear();
    }
}