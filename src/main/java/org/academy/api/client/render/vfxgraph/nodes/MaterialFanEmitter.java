package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.List;

import static org.academy.api.client.render.vfxgraph.nodes.FeatherArrayEmitter.*;

/** Continuous, graph-authored annular material blade, independent of particle trail history. */
public final class MaterialFanEmitter {
    private MaterialFanEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("radius", 4.4f), number("arc", 2.2f), number("width", 0.9f),
                number("lifetime", 0.32f), number("opacity", 0.82f), number("height", 1.2f),
                text("radius_curve"), text("width_curve"), text("opacity_curve"));
    }

    public static SimNode create(VfxBlock block) {
        var slots = new ShapeParticleSlots(1);
        return (buffer, context) -> {
            slots.update(buffer, context);
            float lifetime = Math.clamp(property(block, "lifetime", 0.32f), 0.05f, 4);
            float time = live(context, "time", context.time());
            if (time < 0) time = context.time();
            float progress = Math.clamp(time / lifetime, 0, 1);
            float radius = Math.clamp(live(context, "radius", property(block, "radius", 4.4f)), 0.1f, 64);
            float width = Math.clamp(live(context, "width", property(block, "width", 0.9f)), 0.02f, radius);
            radius = Math.clamp(radius * Math.clamp(curve(context, block, "radius_curve", progress, 1), 0.01f, 2), 0.1f, 64);
            width *= Math.clamp(curve(context, block, "width_curve", progress, 1), 0.01f, 2);
            width = Math.clamp(width, 0.02f, radius);
            float arc = Math.clamp(live(context, "arc", property(block, "arc", 2.2f)), 0.05f, 5.8f);
            float envelope = smooth(time / Math.min(0.035f, lifetime * 0.2f)) * smooth((lifetime - time) / (lifetime * 0.65f));
            envelope *= Math.clamp(curve(context, block, "opacity_curve", progress, 1), 0, 1);
            int particle = slots.index(0);
            buffer.setPosition(particle, 0, live(context, "height", property(block, "height", 1.2f)), 0);
            buffer.setVelocity(particle, 0, 0, radius);
            buffer.setSurfaceNormal(particle, 0, 1, 0);
            buffer.setSize(particle, arc);
            buffer.setRotation(particle, width / radius * (live(context, "reverse", 0) > 0.5f ? -1 : 1));
            buffer.setColor(particle, 0.98f, 0.975f, 0.96f,
                    Math.clamp(property(block, "opacity", 0.82f) * live(context, "strength", 1), 0, 1) * envelope);
            buffer.setAge(particle, progress);
            buffer.setLifetime(particle, 1);
            buffer.setLayer(particle, ParticleBuffer.layerByte("material_fan"));
        };
    }
}
