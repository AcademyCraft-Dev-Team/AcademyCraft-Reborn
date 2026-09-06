package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.StormCloudShape;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.*;

/** Independent, short surface traces with local forks. No common center or radial spokes. */
public final class SurfaceDischargeEmitter {
    private SurfaceDischargeEmitter() {
    }

    public static List<PropertySpec> properties() {
        return List.of(number("duration", 3.4f), number("fade", 0.45f), number("height", 72),
                number("ground_radius", 14), number("ground_arcs", 12), number("cloud_radius", 20),
                number("cloud_count", 44), number("cloud_arcs", 12), number("width", 0.055f),
                number("crawl_rate", 5));
    }

    public static SimNode create(VfxBlock block, PortValueSource ports) {
        long group = SkyDischargeEmitter.newGroup();
        float[] epoch = {Float.NaN};
        return (buffer, context) -> {
            context.arcs().removeGroup(group);
            if (!Float.isFinite(epoch[0])) epoch[0] = context.time();
            float override = context.paramFloat("time", -1);
            float t = Math.max(0, override >= 0 ? override : context.time() - epoch[0]);
            float duration = value(block, context, "duration", 3.4f, 0.1f, 20);
            float fade = value(block, context, "fade", 0.45f, 0.01f, duration);
            float alpha = smooth((t - 0.035f) / 0.10f)
                    * (1f - smooth((t - duration + fade) / fade))
                    * value(block, context, "opacity", 1, 0, 1);
            float detail = value(block, context, "detail", 1, 0, 1);
            if (alpha < 0.002f || detail <= 0) return;
            long seed = (long) context.paramFloat("seed", 42);
            int frame = (int) (t * value(block, context, "crawl_rate", 5, 0, 15));
            float height = value(block, context, "height", 72, 1, 192);
            float cloudRadius = value(block, context, "cloud_radius", 20, 1, 48);
            int cloudCount = Math.round(value(block, context, "cloud_count", 44, 6, 64) * (0.35f + detail * 0.65f));
            var lobes = new Vector4f[cloudCount];
            for (int i = 0; i < cloudCount; i++) {
                lobes[i] = StormCloudShape.lobe(i, cloudCount, t, seed, height, cloudRadius, new Vector4f());
            }
            SurfaceProjector cloud = (x, y, z, out) ->
                    out.set(x, StormCloudShape.underside(x, z, lobes) - 0.07f, z);
            float width = value(block, context, "width", 0.055f, 0.005f, 0.25f);
            for (int layer = 0; layer < 2; layer++) {
                boolean sky = layer == 1;
                var surface = sky ? cloud : context.surface("ground");
                float reach = sky ? cloudRadius : value(block, context, "ground_radius", 14, 1, 32);
                int count = Math.round(value(block, context, sky ? "cloud_arcs" : "ground_arcs", 12, 0, 24) * detail);
                float layerAlpha = alpha * (sky ? value(block, context, "cloud_opacity", 1, 0, 1) : 1f);
                for (int patch = 0; patch < count; patch++) {
                    long patchSeed = seed + 3001 + layer * 9001L + patch * 137L;
                    float angle = unit(patchSeed, 0) * 6.283185f;
                    float cx;
                    float cz;
                    if (sky) {
                        var lobe = lobes[(patch * 7 + 3) % cloudCount];
                        cx = lobe.x;
                        cz = lobe.z;
                    } else {
                        float centerAngle = unit(patchSeed, 4) * 6.283185f;
                        float distance = reach * (float) Math.sqrt(unit(patchSeed, 5)) * 0.74f;
                        cx = (float) Math.cos(centerAngle) * distance;
                        cz = (float) Math.sin(centerAngle) * distance;
                    }
                    float length = reach * (0.22f + unit(patchSeed, 2) * 0.18f);
                    float flicker = 0.60f + unit(patchSeed + frame * 31L, 8) * 0.40f;
                    var main = trace(context, group, surface, cx, cz, angle, length,
                            width, layerAlpha * flicker, patchSeed, frame, sky);
                    if (main.size() < 4) continue;
                    int at = main.size() / 2;
                    trace(context, group, surface, main.x(at), main.z(at), angle + 1.15f,
                            length * 0.48f, width * 0.50f, layerAlpha * flicker * 0.7f,
                            patchSeed + 99, frame, sky);
                }
            }
        };
    }

    private static ArcCurve trace(SimContext context, long group, SurfaceProjector surface,
                                  float cx, float cz, float angle, float length, float width,
                                  float alpha, long seed, int frame, boolean sky) {
        var arc = context.arcs().add(group);
        arc.setColor(0.40f, 0.80f, 1f, alpha);
        arc.setSeed(seed);
        arc.setLifetime(1);
        arc.setNoiseStrength(0);
        arc.setDriftSpeed(0);
        var point = new Vector3f();
        var previous = new Vector3f();
        int stroke = 0;
        boolean hasPrevious = false;
        for (int i = 0; i <= 48; i++) {
            float u = i / 48f;
            float bend = (float) Math.sin(u * 6.5f) * length * 0.24f
                    + (unit(seed + frame * 71L, i / 2) - 0.5f) * length * 0.11f * (float) Math.sin(u * Math.PI);
            float x = cx + (float) Math.cos(angle) * length * u - (float) Math.sin(angle) * bend;
            float z = cz + (float) Math.sin(angle) * length * u + (float) Math.cos(angle) * bend;
            surface.project(x, 0.08f, z, point);
            if (!Float.isFinite(point.y)) {
                stroke++;
                hasPrevious = false;
                continue;
            }
            float thickness = width * (0.45f + 0.55f * (float) Math.sin(u * Math.PI));
            // At block steps, follow the riser before the tread instead of cutting diagonally through blocks.
            if (!sky && hasPrevious && Math.abs(point.y - previous.y) > 0.3f) {
                if (point.y > previous.y) {
                    arc.addPoint(previous.x, point.y, previous.z, thickness, 0, stroke);
                } else {
                    arc.addPoint(point.x, previous.y, point.z, thickness, 0, stroke);
                }
            }
            arc.addPoint(point.x, point.y, point.z, thickness, 0, stroke);
            previous.set(point);
            hasPrevious = true;
        }
        return arc;
    }

    private static float value(VfxBlock block, SimContext context, String key, float fallback, float min, float max) {
        float property = fallback;
        try {
            property = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
        }
        float result = context.paramFloat(key, property);
        return Float.isFinite(result) ? Math.clamp(result, min, max) : fallback;
    }

    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id.replace('_', ' '), ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
