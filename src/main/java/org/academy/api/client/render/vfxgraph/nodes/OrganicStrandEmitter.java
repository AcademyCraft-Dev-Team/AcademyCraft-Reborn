package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded, deterministic translucent strands. Geometry is local +Y, independent of a skill or actor. */
public final class OrganicStrandEmitter {
    private static final AtomicLong GROUPS = new AtomicLong(-1000000);

    private OrganicStrandEmitter() {}

    public static SimNode create(VfxBlock block) {
        int mode = (int) number(block, "mode", 0);
        int count = Math.clamp((int) number(block, "count", 12), 1, 48);
        int segments = Math.clamp((int) number(block, "segments", 80), 16, 128);
        float duration = Math.max(0.01f, number(block, "duration", 0.65f));
        float radius = number(block, "radius", 0.65f);
        float length = number(block, "length", 16f);
        float speed = number(block, "speed", 8f);
        float thickness = number(block, "thickness", 0.04f);
        float opacity = number(block, "opacity", 0.6f);
        long group = GROUPS.getAndDecrement();
        return (buffer, context) -> {
            context.arcs().removeGroup(group);
            float time = context.paramFloat("time", context.time());
            if (time < 0) time = context.time();
            float age = time / Math.max(0.01f, context.paramFloat("duration", duration));
            if (age < 0 || age >= 1) return;
            float fade = Math.min(1f, age * 12f) * Math.min(1f, (1f - age) * 4f);
            float liveLength = Math.clamp(context.paramFloat("length", length), 0.01f, 512f);
            float liveRadius = Math.clamp(context.paramFloat("width", radius), 0.01f, 32f);
            float height = Math.clamp(context.paramFloat("height", 1.8f), 0.1f, 32f);
            float seed = context.paramFloat("seed", 42f);
            for (int strand = 0; strand < count; strand++) {
                var arc = context.arcs().add(group);
                arc.setLayer("organic");
                arc.setMaxTubeSegments(6);
                float phase = strand * 2.399963f + seed * 0.017f;
                for (int point = 0; point <= segments; point++) {
                    float u = point / (float) segments;
                    float envelope = (float) Math.sin(Math.PI * u);
                    float wave = (float) Math.sin(u * 19f + phase + time * speed);
                    float angle = phase + u * (mode == 1 ? 13f : 7f) - time * speed;
                    float r, y;
                    if (mode == 0) {
                        // Straight light blades with soft organic edges; their endpoints stay on the ray.
                        r = liveRadius * envelope * (0.35f + 0.65f * strand / count) * (1f + wave * 0.13f);
                        angle = phase + wave * 0.14f;
                        y = liveLength * u;
                    } else if (mode == 1) {
                        r = liveRadius * (0.85f + wave * 0.13f + 0.06f * (float) Math.sin(u * 37f + phase)) * (1f - age * 0.25f);
                        y = height * (u * 0.65f + 0.35f * strand / count);
                    } else {
                        // Short curling wisps form a porous cloud, expanding as the target breaks apart.
                        r = liveRadius * (0.5f + 0.5f * (float) Math.sin(phase * 3f + u * 2f))
                                * (0.8f + age * 0.8f);
                        y = height * 0.5f + (float) Math.sin(phase + u * 4f) * height * 0.55f;
                    }
                    float width = thickness * (0.12f + 0.88f * envelope) * (1f + wave * 0.28f);
                    arc.addPoint((float) Math.cos(angle) * r, y, (float) Math.sin(angle) * r, width, 0f);
                }
                arc.setColor(1f, 1f, 1f, opacity * fade);
                arc.setLifetime(1f);
                arc.setSeed((long) seed + strand);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }

    private static float number(VfxBlock block, String key, float fallback) {
        try { return Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
