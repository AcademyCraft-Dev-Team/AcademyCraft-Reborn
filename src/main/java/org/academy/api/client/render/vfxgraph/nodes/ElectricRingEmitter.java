package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.List;

import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.*;
import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** Bounded expanding/contracting rings using the same shell and core as endpoint discharges. */
public final class ElectricRingEmitter {
    private ElectricRingEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("radius", 8), number("radial_speed", 1.6f), number("height", 1),
                number("width", 0.065f), number("duration", 0.5f), number("phase", 0),
                number("opacity", 1), number("detail", 1), number("flicker_rate", 18));
    }

    public static SimNode create(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        var path = new ArcCurve();
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            if (time >= value(block, ctx, "duration", 0.5f, 0.05f, 20)) return;
            float opacity = value(block, ctx, "opacity", 1, 0, 1);
            if (opacity <= 0.001f) return;
            float radius = Math.max(0, value(block, ctx, "radius", 8, 0, 4096)
                    + time * value(block, ctx, "radial_speed", 1.6f, -4096, 4096));
            if (radius <= 0.001f) return;
            float phase = time + value(block, ctx, "phase", 0, 0, 1e8f);
            float height = value(block, ctx, "height", 1, -4096, 4096);
            float width = value(block, ctx, "width", 0.065f, 0.001f, 1);
            float detail = value(block, ctx, "detail", 1, 0, 1);
            long seed = (long) ctx.paramFloat("seed", 42)
                    + (long) (phase * value(block, ctx, "flicker_rate", 18, 1, 40)) * 911;
            int segments = Math.clamp((int) Math.ceil(radius * 6), 32, 128);
            int highlights = Math.round(3 * detail);
            // One closed rim and at most three hot sections; no recursive branches or sparks.
            for (int piece = 0; piece <= highlights; piece++) {
                boolean rim = piece == 0;
                int count = rim ? segments : 16;
                float start = rim ? 0 : phase * 1.6f + piece * 2.094395f;
                float span = rim ? (float) (Math.PI * 2) : 0.55f;
                path.clearPoints();
                for (int i = 0; i <= count; i++) {
                    float u = (float) i / count;
                    float angle = rim && i == count ? 0 : start + u * span;
                    float turn = angle / (float) (Math.PI * 2);
                    turn -= (float) Math.floor(turn);
                    float cursor = turn * 64;
                    int cell = (int) cursor;
                    float a = unit(seed, cell), b = unit(seed, (cell + 1) % 64);
                    float jitter = (a + (b - a) * (cursor - cell) - 0.5f) * Math.min(0.35f, radius * 0.15f);
                    float r = radius + jitter;
                    float y = height + (float) Math.sin(angle * 3 + phase * 2) * Math.min(0.16f, radius * 0.08f);
                    float taper = rim ? 0.45f : 0.45f + 0.55f * (float) Math.sin(u * Math.PI);
                    path.addPoint((float) Math.cos(angle) * r, y, (float) Math.sin(angle) * r,
                            width * taper, 0);
                }
                emit(ctx, path, group, seed + piece, opacity, "electricity");
            }
        };
    }
}
