package org.academy.api.client.gui.animation;

@FunctionalInterface
public interface TimeInterpolator {
    float getInterpolation(float input);
}