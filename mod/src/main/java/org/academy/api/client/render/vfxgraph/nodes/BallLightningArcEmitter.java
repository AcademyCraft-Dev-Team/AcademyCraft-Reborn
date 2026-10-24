package org.academy.api.client.render.vfxgraph.nodes;

import net.minecraft.util.Mth;
import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.vfxgraph.arc.ArcCurveBuilder;
import org.academy.api.client.render.vfxgraph.arc.ArcEmission;
import org.academy.api.client.render.vfxgraph.arc.ArcGroups;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.List;
import java.util.Random;

import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.number;
import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.time;
import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.value;

public final class BallLightningArcEmitter {
    private BallLightningArcEmitter() {
    }

    public static List<PropertySpec> properties() {
        return List.of(number("radius", 0.2f), number("ring_count", 8), number("ring_segments", 12),
                number("branch_count", 4), number("opacity", 1), number("detail", 1));
    }

    public static SimNode create(VfxBlock block, PortValueSource ports) {
        long group = ArcGroups.next();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float opacity = value(block, ctx, "opacity", 1, 0, 1);
            if (opacity <= 0.001f) return;
            float detail = value(block, ctx, "detail", 1, 0, 1);
            float radius = value(block, ctx, "radius", 0.2f, 0.01f, 8);
            int ringCount = Math.max(1, Math.round(value(block, ctx, "ring_count", 8, 1, 16) * detail));
            int ringSegments = Math.max(6, Math.round(value(block, ctx, "ring_segments", 12, 6, 32) * detail));
            int branchCount = Math.round(value(block, ctx, "branch_count", 4, 0, 12) * detail);
            long seed = (long) ctx.paramFloat("seed", 42);
            var out = new ArcEmission(ctx.arcs(), group, 128, 128, "electricity");
            var random = new Random(seed);

            for (var i = 0; i < ringCount; i++) {
                var breath = Mth.sin(time * 0.5f + i * 0.785f);
                var ringRadius = radius * (1f + 0.8f * breath);
                var rotSpeed = 1f + 0.6f * ((i * 12345L) % 100 / 100f);
                var theta = time * rotSpeed + i;
                var phi = i * 2.399963f;
                var nx = Mth.cos(theta) * Mth.sin(phi);
                var ny = Mth.cos(phi);
                var nz = Mth.sin(theta) * Mth.sin(phi);
                var ringSeed = seed + 1000L + i * 123L;
                ArcCurveBuilder.ring(out, 0, 0, 0, nx, ny, nz, ringRadius, ringSegments,
                        radius * 0.12f, 0.12f, 0.48f, 1f, opacity * 0.48f, 1f, ringSeed);
                ArcCurveBuilder.ring(out, 0, 0, 0, nx, ny, nz, ringRadius, ringSegments,
                        radius * 0.035f, 0.78f, 0.94f, 1f, opacity * 0.92f, 1f, ringSeed + 1);
            }

            for (var i = 0; i < branchCount; i++) {
                var theta = random.nextDouble() * Mth.TWO_PI;
                var phi = Math.acos(2 * random.nextDouble() - 1);
                var distance = radius * (2f + random.nextDouble() * 4f);
                var ex = (float) (distance * Math.sin(phi) * Math.cos(theta));
                var ey = (float) (distance * Math.cos(phi));
                var ez = (float) (distance * Math.sin(phi) * Math.sin(theta));
                ArcCurveBuilder.blueWhiteLine(out, 0, 0, 0, ex, ey, ez,
                        radius * 0.12f, 8, opacity, 1f, seed + i * 211L,
                        2, 2, 1.2f, 0.35f, 0.4f);
            }
        };
    }
}
