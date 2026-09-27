package org.academy.api.client.render.graph.type;

import java.util.List;

/**
 * 渐变（值载体）。按位置采样的颜色停靠序列（VFX 图使用）。
 */
public record Gradient(List<ColorStop> stops) {
    public Gradient {
        stops = List.copyOf(stops);
    }

    public record ColorStop(float position, float r, float g, float b, float a) {
    }
}
