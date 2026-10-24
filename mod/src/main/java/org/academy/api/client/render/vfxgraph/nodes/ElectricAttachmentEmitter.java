package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.ArcCurveBuilder;
import org.academy.api.client.render.vfxgraph.arc.ArcEmission;
import org.academy.api.client.render.vfxgraph.arc.ArcGroups;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.nodes.ElectricArcEmitter.*;
import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

public final class ElectricAttachmentEmitter {
    private ElectricAttachmentEmitter() {
    }

    public static List<PropertySpec> shieldProperties() {
        return List.of(number("radius", 0.85f), number("height", 1.8f), number("width", 0.035f),
                number("duration", 0.25f), number("phase", 0), number("impact", 0),
                number("opacity", 1), number("detail", 1), number("flicker_rate", 18));
    }

    public static List<PropertySpec> pathProperties() {
        var properties = new ArrayList<>(List.of(number("width", 0.035f), number("opacity", 1),
                number("flicker_rate", 18), number("max_paths", 16), number("max_points", 128)));
        properties.add(new PropertySpec("source", "Path source", ValueType.STRING,
                Value.string("paths"), Optional.empty()));
        return List.copyOf(properties);
    }

    public static SimNode shield(VfxBlock block, PortValueSource ports) {
        long group = ArcGroups.next();
        float[] epoch = {Float.NaN};
        var path = new ArcCurve();
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float duration = value(block, ctx, "duration", 0.25f, 0.05f, 10);
            if (time >= duration) return;
            float alpha = value(block, ctx, "opacity", 1, 0, 1);
            boolean impact = value(block, ctx, "impact", 0, 0, 1) > 0.5f;
            float radius = value(block, ctx, "radius", 0.85f, 0.1f, 16);
            float height = value(block, ctx, "height", 1.8f, 0.2f, 32);
            float width = value(block, ctx, "width", 0.035f, 0.001f, 1);
            float phase = time + value(block, ctx, "phase", 0, 0, 1e8f);
            int frame = (int) (phase * value(block, ctx, "flicker_rate", 18, 1, 40));
            long seed = (long) ctx.paramFloat("seed", 42) + frame * 911L;
            int count = impact ? 2 : Math.max(3, Math.round(6 * value(block, ctx, "detail", 1, 0, 1)));
            for (int i = 0; i < count; i++) {
                path.clearPoints();
                for (int p = 0; p <= 32; p++) {
                    float u = p / 32f;
                    float a = phase * 2.6f + i * 2.399963f + u * (impact ? 6.283185f : 1.15f);
                    float jitter = (unit(seed + i * 317L, p % 32) - 0.5f) * 0.10f;
                    float r = radius * (impact ? (i == 0 ? 1 : 0.68f) * (0.8f + time / duration * 0.3f) : 1);
                    float y = impact ? jitter * 0.25f : height * (0.18f + 0.32f * (i % 3))
                            + height * 0.13f * (float) Math.sin(a * 1.7f + i) + jitter;
                    float taper = impact ? 0.7f : 0.12f + 0.88f * (float) Math.sin(Math.PI * u);
                    path.addPoint((float) Math.cos(a) * (r + jitter), y,
                            (float) Math.sin(a) * (r + jitter), width * taper, 0);
                }
                float fade = impact ? 1 - time / duration : 0.82f + unit(seed, i + 40) * 0.18f;
                emit(ctx, path, group, seed + i, alpha * fade, "electricity");
            }
        };
    }

    public static SimNode paths(VfxBlock block, PortValueSource ports) {
        long group = ArcGroups.next();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float opacity = value(block, ctx, "opacity", 1, 0, 1);
            if (opacity <= 0.001f) return;
            var source = ctx.arcSource(block.properties().getOrDefault("source", "paths"));
            var maxPaths = Math.round(value(block, ctx, "max_paths", 16, 1, 64));
            var maxPoints = Math.round(value(block, ctx, "max_points", 128, 8, 128));
            float width = value(block, ctx, "width", 0.035f, 0.001f, 1);
            var out = new ArcEmission(ctx.arcs(), group, maxPaths * 2, maxPoints, "electricity");
            if (source == null) {
                preview(out, time, width, opacity);
            } else {
                source.emit(out, time);
            }
        };
    }

    private static void preview(ArcEmission out, float time, float width, float opacity) {
        for (int i = 0; i < 4; i++) {
            float a = time * 4 + i * 1.570796f;
            ArcCurveBuilder.blueWhiteLine(out, 0, -1, 0,
                    (float) Math.cos(a) * 0.7f, 1, (float) Math.sin(a) * 0.7f,
                    width, 5, opacity, 1f, i,
                    1, 2, 1.2f, 0.35f, 0.4f);
        }
    }
}
