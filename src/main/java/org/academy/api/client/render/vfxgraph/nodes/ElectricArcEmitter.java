package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.BlueWhiteArcStyle;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** Analytic blue-white discharges: exact +Y endpoint bolts and filament-lined charging rings. */
public final class ElectricArcEmitter {
    private ElectricArcEmitter() { }

    public static List<PropertySpec> boltProperties() {
        return List.of(number("length", 10), number("width", 0.065f), number("spread", 0.65f),
                number("strands", 3), number("forks", 2), number("duration", 1),
                number("flicker_rate", 18), number("opacity", 1), number("density", 1), number("detail", 1));
    }

    public static List<PropertySpec> orbitProperties() {
        return List.of(number("radius", 0.30f), number("width", 0.014f), number("rim_width", 0.0055f), number("strength", 1),
                number("filaments", 4), number("filament_reach", 1.15f),
                number("motion_speed", 1), number("motion_strength", 1),
                number("hint", 0), number("flicker_rate", 18), number("opacity", 1));
    }

    public static SimNode bolt(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        var path = new ArcCurve();
        var fork = new ArcCurve();
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float duration = value(block, ctx, "duration", 1, 0.05f, 20);
            float length = value(block, ctx, "length", 10, 0, 4096);
            if (time >= duration || length <= 0) return;
            float opacity = value(block, ctx, "opacity", 1, 0, 1)
                    * value(block, ctx, "density", 1, 0, 2)
                    * (1 - smooth((time / duration - 0.35f) / 0.65f));
            if (opacity <= 0.001f) return;
            float detail = value(block, ctx, "detail", 1, 0, 1);
            float width = value(block, ctx, "width", 0.065f, 0.001f, 2);
            float spread = Math.min(length * 0.12f, value(block, ctx, "spread", 0.65f, 0, 8));
            int count = Math.max(1, Math.round(value(block, ctx, "strands", 3, 1, 8) * detail));
            int forks = Math.round(value(block, ctx, "forks", 2, 0, 5) * detail);
            int frame = (int) (time * value(block, ctx, "flicker_rate", 18, 1, 40));
            int segments = Math.clamp((int) Math.ceil(length * 2), 24, 96);
            long seed = (long) ctx.paramFloat("seed", 42);
            for (int strand = 0; strand < count; strand++) {
                long random = seed + strand * 317L + frame * 911L;
                float angle = strand * 2.399963f;
                path.clearPoints();
                for (int i = 0; i <= segments; i++) {
                    float u = (float) i / segments;
                    float envelope = (float) Math.sin(u * Math.PI);
                    float bow = strand == 0 ? 0.18f : 0.45f + strand * 0.12f;
                    float x = spread * envelope * ((float) Math.cos(angle) * bow + noise(random, u, 13) * 0.42f);
                    float z = spread * envelope * ((float) Math.sin(angle) * bow + noise(random + 53, u, 11) * 0.42f);
                    float taper = (0.18f + 0.82f * smooth(u * 12)) * (0.08f + 0.92f * smooth((1 - u) * 10));
                    path.addPoint(x, u * length, z, width * (strand == 0 ? 1 : 0.6f) * taper, 0);
                }
                float flicker = 0.82f + unit(random, 51) * 0.18f;
                emit(ctx, path, group, random, opacity * flicker, "electricity");
                if (strand != 0) continue;
                for (int branch = 0; branch < forks; branch++) {
                    int at = Math.clamp(Math.round(segments * (0.2f + unit(random, branch + 70) * 0.55f)), 1, segments - 1);
                    fork.clearPoints();
                    float branchAngle = angle + branch * 2.399963f + unit(random, branch + 90) * 3;
                    float reach = Math.min(length - path.y(at), Math.max(0.2f, spread * 2.5f));
                    for (int p = 0; p <= 12; p++) {
                        float u = p / 12f;
                        float lateral = spread * u * (0.75f + 0.20f * noise(random + branch, u, 7));
                        fork.addPoint(path.x(at) + (float) Math.cos(branchAngle) * lateral,
                                path.y(at) + u * reach, path.z(at) + (float) Math.sin(branchAngle) * lateral,
                                width * 0.42f * (1 - u), 1);
                    }
                    emit(ctx, fork, group, random + branch + 100, opacity * 0.72f, "electricity");
                }
            }
        };
    }

    public static SimNode orbit(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        var path = new ArcCurve();
        var point = new Vector3f();
        var rotation = new Quaternionf();
        var branch = new ArcCurve();
        return (buffer, ctx) -> {
            ctx.arcs().removeGroup(group);
            float time = time(ctx, epoch);
            float strength = value(block, ctx, "strength", 1, 0, 1);
            float alpha = value(block, ctx, "opacity", 1, 0, 1) * (0.30f + strength * 0.70f);
            if (alpha <= 0.001f) return;
            boolean hint = value(block, ctx, "hint", 0, 0, 1) > 0.5f;
            float motionTime = time * value(block, ctx, "motion_speed", 1, 0, 4);
            float motion = value(block, ctx, "motion_strength", 1, 0, 2) * (hint ? 0.35f : 1);
            float radius = value(block, ctx, "radius", 0.30f, 0.02f, 8) * (0.75f + strength * 0.25f);
            radius *= 1 + motion * (0.045f * (float) Math.sin(motionTime * 7.3f)
                    + 0.025f * (float) Math.sin(motionTime * 12.1f + 0.6f));
            float width = value(block, ctx, "width", 0.014f, 0.001f, 0.25f) * (0.6f + strength * 0.4f);
            float rimWidth = value(block, ctx, "rim_width", 0.0055f, 0.0005f, 0.15f) * (0.6f + strength * 0.4f);
            int frame = (int) (motionTime * value(block, ctx, "flicker_rate", 18, 1, 40));
            long seed = (long) ctx.paramFloat("seed", 42);
            long random = seed + frame * 911L;
            rotation.identity().rotateX(0.28f + motion * (float) Math.sin(motionTime * 3.7f) * 0.16f)
                    .rotateY(0.20f + motion * (float) Math.sin(motionTime * 2.9f + 1) * 0.22f)
                    .rotateZ(motion * (float) Math.sin(motionTime * 1.5f) * 0.10f);
            // A substantial continuous rim remains readable between the sparse white-hot sections.
            path.clearPoints();
            for (int i = 0; i <= 64; i++) {
                float angle = i * (float) (Math.PI * 2) / 64;
                ringPoint(random, angle, radius, motionTime, motion, rotation, point);
                float flow = 1 + motion * 0.15f * (float) Math.sin(angle * 2 - motionTime * 12);
                path.addPoint(point.x, point.y, point.z, rimWidth * flow, 0);
            }
            emit(ctx, path, group, random, alpha * 0.85f, "electricity");
            for (int piece = 0; piece < (hint ? 1 : 3); piece++) {
                float phase = motionTime * (1.6f + piece * 0.12f) + piece * 2.094395f
                        + motion * (float) Math.sin(motionTime * 2.2f + piece) * 0.38f;
                float span = 0.48f + unit(random, piece + 10) * 0.34f;
                path.clearPoints();
                for (int i = 0; i <= 20; i++) {
                    float u = i / 20f;
                    ringPoint(random, phase + span * u, radius, motionTime, motion, rotation, point);
                    float hot = (float) Math.pow(Math.sin(u * Math.PI), 0.8);
                    path.addPoint(point.x, point.y, point.z, width * (0.10f + hot), 0);
                }
                emit(ctx, path, group, random + piece, alpha, "electricity");
            }
            int filaments = hint ? 0 : Math.round(value(block, ctx, "filaments", 4, 0, 6));
            float reach = value(block, ctx, "filament_reach", 1.15f, 0, 2);
            for (int tail = 0; tail < filaments; tail++) {
                float angle = tail * 2.399963f + motionTime * 0.55f
                        + motion * (float) Math.sin(motionTime * 3.5f + tail * 1.7f) * 0.45f;
                ringPoint(random, angle, radius, motionTime, motion, rotation, point);
                float startX = point.x, startY = point.y, startZ = point.z;
                float direction = angle + (unit(random, tail + 30) - 0.5f) * 1.3f;
                float pulse = 0.5f + 0.5f * (float) Math.sin(motionTime * (7 + tail) + tail * 1.7f);
                float length = radius * reach * (0.25f + pulse * 0.75f);
                float surge = tail % 2 == 0 ? smooth((pulse - 0.40f) / 0.60f) : 0;
                path.clearPoints();
                for (int i = 0; i <= 12; i++) {
                    float u = i / 12f;
                    float drift = noise(random + tail * 73L, u, 7) * radius * 0.17f * u;
                    point.set((float) Math.cos(direction) * length * u - (float) Math.sin(direction) * drift,
                            (float) Math.sin(direction) * length * u + (float) Math.cos(direction) * drift,
                            drift * 0.4f).rotate(rotation);
                    path.addPoint(startX + point.x, startY + point.y, startZ + point.z,
                            width * (0.16f + surge * 0.52f) * (1 - u * 0.92f), 0);
                }
                emit(ctx, path, group, random + tail + 50, alpha * 0.86f, "electricity");
                // Thin forks inherit their root from the parent filament instead of radiating from the hand.
                branch.clearPoints();
                for (int i = 0; i <= 6; i++) {
                    float u = i / 6f;
                    point.set((float) Math.cos(direction + 0.85f) * length * u * 0.48f,
                            (float) Math.sin(direction + 0.85f) * length * u * 0.48f,
                            noise(random + tail, u, 4) * radius * 0.10f * u).rotate(rotation);
                    branch.addPoint(path.x(5) + point.x, path.y(5) + point.y, path.z(5) + point.z,
                            width * 0.09f * (1 - u), 0);
                }
                emit(ctx, branch, group, random + tail + 70, alpha * 0.68f, "electricity");
            }
        };
    }

    private static void ringPoint(long seed, float angle, float radius, float time, float motion,
                                  Quaternionf rotation, Vector3f point) {
        float turn = angle / (float) (Math.PI * 2);
        turn -= (float) Math.floor(turn);
        float cursor = turn * 24;
        int cell = (int) cursor;
        float a = unit(seed, cell + 100), b = unit(seed, (cell + 1) % 24 + 100);
        float r = radius * (0.94f + 0.12f * (a + (b - a) * (cursor - cell)));
        r *= 1 + motion * (0.06f * (float) Math.sin(angle * 3 - time * 6)
                + 0.035f * (float) Math.sin(angle * 5 + time * 9));
        point.set((float) Math.cos(angle) * r, (float) Math.sin(angle) * r,
                radius * (0.045f * (float) Math.sin(angle * 3)
                        + motion * 0.12f * (float) Math.sin(angle * 2 + time * 4))).rotate(rotation);
    }

    static void emit(SimContext ctx, ArcCurve path, long group, long seed, float opacity, String layer) {
        for (int shell = 0; shell < 2; shell++) {
            var arc = BlueWhiteArcStyle.shell(ctx.arcs(), group, seed, opacity, shell, layer);
            for (int i = 0; i < path.size(); i++) {
                arc.addPoint(path.x(i), path.y(i), path.z(i), path.width(i) * BlueWhiteArcStyle.widthScale(shell),
                        path.generation(i), path.segment(i));
            }
        }
    }

    private static float noise(long seed, float u, int cells) {
        float cursor = u * cells;
        int cell = (int) cursor;
        float a = unit(seed, cell + 150) * 2 - 1;
        float b = unit(seed, cell + 151) * 2 - 1;
        return a + (b - a) * (cursor - cell);
    }

    static float time(SimContext ctx, float[] epoch) {
        if (!Float.isFinite(epoch[0])) epoch[0] = ctx.time();
        float value = ctx.paramFloat("time", -1);
        return Math.max(0, Float.isFinite(value) && value >= 0 ? value : ctx.time() - epoch[0]);
    }

    static float value(VfxBlock block, SimContext ctx, String id, float fallback, float min, float max) {
        float property = fallback;
        try { property = Float.parseFloat(block.properties().getOrDefault(id, Float.toString(fallback))); }
        catch (NumberFormatException ignored) { }
        float value = ctx.paramFloat(id, property);
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
