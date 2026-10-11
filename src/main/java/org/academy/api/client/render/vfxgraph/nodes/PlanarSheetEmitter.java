package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.List;
import java.util.Optional;

/** One continuous local XY sheet and three thin YZ fins, readable even when viewed along the plane. */
public final class PlanarSheetEmitter {
    public static final int SURFACE_COUNT = 4;
    private PlanarSheetEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("length", 24), number("width", 5), number("thickness", 0.06f),
                number("lifetime", 0.6f), number("opacity", 0.34f), number("edge_opacity", 0.7f));
    }

    public static SimNode create(VfxBlock block) {
        float lifetime = Math.clamp(property(block, "lifetime", 0.6f), 0.1f, 2);
        float thickness = Math.clamp(property(block, "thickness", 0.06f), 0.01f, 0.15f);
        float opacity = Math.clamp(property(block, "opacity", 0.34f), 0, 1);
        float edgeOpacity = Math.clamp(property(block, "edge_opacity", 0.7f), 0, 1);
        float defaultLength = property(block, "length", 24);
        float defaultWidth = property(block, "width", 5);
        int[] indices = new int[SURFACE_COUNT];
        boolean[] spawned = {false};
        return (buffer, context) -> {
            if (!spawned[0]) {
                int start = buffer.count();
                for (int i = 0; i < SURFACE_COUNT; i++) indices[i] = buffer.spawn();
                context.emitBatch(start, buffer.count());
                spawned[0] = true;
            }
            float time = context.paramFloat("time", context.time());
            if (!Float.isFinite(time)) time = 0;
            float progress = Math.clamp(time / lifetime, 0, 1);
            float envelope = smooth(time / 0.045f) * smooth((lifetime - time) / (lifetime * 0.65f));
            float length = Math.clamp(context.paramFloat("length", defaultLength), 0.1f, 256);
            float width = Math.clamp(context.paramFloat("width", defaultWidth), 0.1f, 32);
            float halfLength = length / 2;
            for (int i = 0; i < SURFACE_COUNT; i++) {
                int particle = indices[i];
                boolean surface = i == 0;
                float x = surface ? 0 : (i - 2) * width * 0.42f;
                buffer.setPosition(particle, x, halfLength, 0);
                // Velocity transports the normal and half-length through the existing world transform.
                // Size is the aspect ratio, so uniform effect scaling also scales the complete surface.
                buffer.setVelocity(particle, surface ? 0 : halfLength, 0, surface ? halfLength : 0);
                buffer.setSize(particle, surface ? width / length : thickness * 2 / length);
                buffer.setRotation(particle, context.paramFloat(surface ? "sheet_roll" : "edge_roll", 0));
                buffer.setColor(particle, 0.92f, 0.93f, 0.94f, (surface ? opacity : edgeOpacity) * envelope);
                buffer.setAge(particle, progress);
                buffer.setLifetime(particle, 1);
                buffer.setLayer(particle, ParticleBuffer.layerByte("blade"));
            }
        };
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    private static float property(VfxBlock block, String key, float fallback) {
        try {
            float value = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id, ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
