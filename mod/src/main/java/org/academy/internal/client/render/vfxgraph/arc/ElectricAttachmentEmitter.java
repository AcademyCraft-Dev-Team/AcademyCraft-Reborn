package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.curve.CurveEmission;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.VfxNoise.unit;
import static org.academy.internal.client.render.vfxgraph.arc.ElectricArcEmitter.*;

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
        var group = ArcGroups.next();
        float[] epoch = {Float.NaN};
        var path = new ArcCurve();
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            var time = time(ctx, epoch);
            var duration = value(block, ctx, "duration", 0.25f, 0.05f, 10);
            if (time >= duration) return;
            var alpha = value(block, ctx, "opacity", 1, 0, 1);
            var impact = value(block, ctx, "impact", 0, 0, 1) > 0.5f;
            var radius = value(block, ctx, "radius", 0.85f, 0.1f, 16);
            var height = value(block, ctx, "height", 1.8f, 0.2f, 32);
            var width = value(block, ctx, "width", 0.035f, 0.001f, 1);
            var phase = time + value(block, ctx, "phase", 0, 0, 1e8f);
            var frame = (int) (phase * value(block, ctx, "flicker_rate", 18, 1, 40));
            var seed = (long) ctx.paramFloat("seed", 42) + frame * 911L;
            var count = impact ? 2 : Math.max(3, Math.round(6 * value(block, ctx, "detail", 1, 0, 1)));
            for (var i = 0; i < count; i++) {
                path.clearPoints();
                for (var p = 0; p <= 32; p++) {
                    var u = p / 32f;
                    var a = phase * 2.6f + i * 2.399963f + u * (impact ? 6.283185f : 1.15f);
                    var jitter = (unit(seed + i * 317L, p % 32) - 0.5f) * 0.10f;
                    var r = radius * (impact ? (i == 0 ? 1 : 0.68f) * (0.8f + time / duration * 0.3f) : 1);
                    var y = impact ? jitter * 0.25f : height * (0.18f + 0.32f * (i % 3))
                            + height * 0.13f * (float) Math.sin(a * 1.7f + i) + jitter;
                    var taper = impact ? 0.7f : 0.12f + 0.88f * (float) Math.sin(Math.PI * u);
                    path.addPoint((float) Math.cos(a) * (r + jitter), y,
                            (float) Math.sin(a) * (r + jitter), width * taper, 0);
                }
                var fade = impact ? 1 - time / duration : 0.82f + unit(seed, i + 40) * 0.18f;
                emit(ctx, path, group, seed + i, alpha * fade, "electricity");
            }
        };
    }

    public static SimNode paths(VfxBlock block, PortValueSource ports) {
        var group = ArcGroups.next();
        float[] epoch = {Float.NaN};
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            var time = time(ctx, epoch);
            var opacity = value(block, ctx, "opacity", 1, 0, 1);
            if (opacity <= 0.001f) return;
            var source = ctx.arcSource(block.properties().getOrDefault("source", "paths"));
            var maxPaths = Math.round(value(block, ctx, "max_paths", 16, 1, 64));
            var maxPoints = Math.round(value(block, ctx, "max_points", 128, 8, 128));
            var width = value(block, ctx, "width", 0.035f, 0.001f, 1);
            var out = new CurveEmission(ctx.arcs(), group, maxPaths * 2, maxPoints, "electricity");
            if (source == null) {
                preview(out, time, width, opacity);
            } else {
                source.emit(out, time);
            }
        };
    }

    private static void preview(CurveEmission out, float time, float width, float opacity) {
        for (var i = 0; i < 4; i++) {
            var a = time * 4 + i * 1.570796f;
            ArcCurveBuilder.blueWhiteLine(out, 0, -1, 0,
                    (float) Math.cos(a) * 0.7f, 1, (float) Math.sin(a) * 0.7f,
                    width, 5, opacity, 1f, i,
                    1, 2, 1.2f, 0.35f, 0.4f);
        }
    }
}
