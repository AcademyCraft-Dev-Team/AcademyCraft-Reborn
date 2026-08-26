package org.academy.api.client.render.graph.type;

import org.joml.Vector4f;

/**
 * 渐变采样器（CPU）：颜色停靠间线性插值。
 */
public final class GradientSampler {
    private GradientSampler() {
    }

    /**
     * 采样 [0,1] 内 t；越界返回首/末停靠颜色。
     */
    public static Vector4f sample(Gradient gradient, float t) {
        var stops = gradient.stops();
        if (stops.isEmpty()) return new Vector4f(1f);
        if (stops.size() == 1) return color(stops.get(0));
        if (t <= stops.get(0).position()) return color(stops.get(0));
        var last = stops.get(stops.size() - 1);
        if (t >= last.position()) return color(last);
        for (int i = 1; i < stops.size(); i++) {
            var b = stops.get(i);
            if (t < b.position()) {
                var a = stops.get(i - 1);
                float span = b.position() - a.position();
                float u = span <= 0f ? 1f : (t - a.position()) / span;
                return new Vector4f(
                        a.r() + (b.r() - a.r()) * u,
                        a.g() + (b.g() - a.g()) * u,
                        a.b() + (b.b() - a.b()) * u,
                        a.a() + (b.a() - a.a()) * u);
            }
        }
        return color(last);
    }

    private static Vector4f color(Gradient.ColorStop stop) {
        return new Vector4f(stop.r(), stop.g(), stop.b(), stop.a());
    }
}
