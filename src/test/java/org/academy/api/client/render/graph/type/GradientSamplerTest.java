package org.academy.api.client.render.graph.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GradientSamplerTest {

    @Test
    void linearBetweenStops() {
        var gradient = new Gradient(List.of(
                new Gradient.ColorStop(0f, 0f, 0f, 0f, 1f),
                new Gradient.ColorStop(1f, 1f, 1f, 1f, 1f)
        ));
        var mid = GradientSampler.sample(gradient, 0.5f);
        assertEquals(0.5f, mid.x, 0.001f);
        assertEquals(0.5f, mid.y, 0.001f);
    }

    @Test
    void clampsOutsideRange() {
        var gradient = new Gradient(List.of(
                new Gradient.ColorStop(0.2f, 1f, 0f, 0f, 1f),
                new Gradient.ColorStop(0.8f, 0f, 1f, 0f, 1f)
        ));
        var start = GradientSampler.sample(gradient, 0f);
        assertEquals(1f, start.x);
        var end = GradientSampler.sample(gradient, 1f);
        assertEquals(0f, end.x);
        assertEquals(1f, end.y);
    }

    @Test
    void emptyReturnsWhite() {
        var color = GradientSampler.sample(new Gradient(List.of()), 0.5f);
        assertEquals(1f, color.x);
        assertEquals(1f, color.y);
        assertEquals(1f, color.z);
        assertEquals(1f, color.w);
    }

    @Test
    void alphaInterpolates() {
        var gradient = new Gradient(List.of(
                new Gradient.ColorStop(0f, 1f, 1f, 1f, 0f),
                new Gradient.ColorStop(1f, 1f, 1f, 1f, 1f)
        ));
        assertEquals(0.5f, GradientSampler.sample(gradient, 0.5f).w, 0.001f);
    }
}
