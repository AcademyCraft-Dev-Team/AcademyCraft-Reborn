package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.StormCloudShape;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.*;

/** Independent, short surface traces with local forks. No common center or radial spokes. */
public final class SurfaceDischargeEmitter {
    private SurfaceDischargeEmitter() {
    }

    public static List<PropertySpec> properties() {
        return List.of(number("duration", 1.5f), number("fade", 0.45f), number("height", 72),
                number("ground_radius", 14), number("ground_arcs", 8), number("cloud_radius", 20),
                number("cloud_count", 44), number("cloud_arcs", 8), number("width", 0.055f),
                number("crawl_rate", 5), number("surface_detail", 1));
    }

    public static SimNode create(VfxBlock block, PortValueSource ports) {
        return new Emitter(block);
    }

    private record Layout(long seed, int frame, float height, float groundRadius, float cloudRadius,
                          int cloudCount, int groundArcs, int cloudArcs, float width, int segments,
                          boolean forks, SurfaceProjector ground) {
    }

    private static final class Trace {
        final ArcCurve path = new ArcCurve();
        boolean sky;
        float brightness;
        long seed;
    }

    private static final class Emitter implements SimNode {
        private final VfxBlock block;
        private final long group = SkyDischargeEmitter.newGroup();
        private final List<Trace> traces = new ArrayList<>();
        private Layout layout;
        private float epoch = Float.NaN;
        private int count;

        private Emitter(VfxBlock block) {
            this.block = block;
        }

        @Override
        public void step(ParticleBuffer buffer, SimContext context) {
            context.arcs().removeGroup(group);
            if (!Float.isFinite(epoch)) epoch = context.time();
            float override = context.paramFloat("time", -1);
            float t = Math.max(0, Float.isFinite(override) && override >= 0 ? override : context.time() - epoch);
            float duration = value(block, context, "duration", 1.5f, 0.1f, 20);
            float fade = value(block, context, "fade", 0.45f, 0.01f, duration);
            float alpha = smooth((t - 0.035f) / 0.10f)
                    * (1f - smooth((t - duration + fade) / fade))
                    * value(block, context, "opacity", 1, 0, 1);
            float detail = value(block, context, "detail", 1, 0, 1);
            float surfaceDetail = detail * value(block, context, "surface_detail", 1, 0, 1);
            if (alpha < 0.002f || surfaceDetail <= 0) return;
            float cloudAlpha = value(block, context, "cloud_opacity", 1, 0, 1);
            var next = new Layout(
                    (long) context.paramFloat("seed", 42),
                    (int) (t * value(block, context, "crawl_rate", 5, 0, 15)),
                    value(block, context, "height", 72, 1, 192),
                    value(block, context, "ground_radius", 14, 1, 32),
                    value(block, context, "cloud_radius", 20, 1, 48),
                    Math.round(value(block, context, "cloud_count", 44, 6, 64) * (0.35f + detail * 0.65f)),
                    Math.round(value(block, context, "ground_arcs", 8, 0, 12) * surfaceDetail),
                    cloudAlpha < 0.002f ? 0 : Math.round(value(block, context, "cloud_arcs", 8, 0, 12) * surfaceDetail),
                    value(block, context, "width", 0.055f, 0.005f, 0.25f),
                    Math.round(10 + 10 * surfaceDetail), surfaceDetail >= 0.7f, context.surface("ground"));
            // Reproject only on a crawl step or a live edit. Cloud paths stay in the billows' local frame.
            if (!next.equals(layout)) {
                rebuild(next);
                layout = next;
            }
            float rotation = StormCloudShape.rotation(t);
            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);
            for (int i = 0; i < count; i++) {
                var trace = traces.get(i);
                float opacity = alpha * trace.brightness * (trace.sky ? cloudAlpha : 1f);
                if (opacity < 0.002f) continue;
                var arc = context.arcs().add(group);
                arc.setColor(0.82f, 0.93f, 1f, opacity);
                arc.setSeed(trace.seed);
                arc.setLifetime(1);
                arc.setNoiseStrength(0);
                arc.setDriftSpeed(0);
                arc.setMaxTubeSegments(surfaceDetail < 0.55f ? 4 : 6);
                var path = trace.path;
                if (!trace.sky) {
                    path.copyRange(arc, 0, path.size());
                } else {
                    for (int p = 0; p < path.size(); p++) {
                        arc.addPoint(path.x(p) * cos - path.z(p) * sin, path.y(p),
                                path.x(p) * sin + path.z(p) * cos,
                                path.width(p), path.generation(p), path.segment(p));
                    }
                }
            }
        }

        private void rebuild(Layout settings) {
            count = 0;
            var lobes = new Vector4f[settings.cloudArcs > 0 ? settings.cloudCount : 0];
            for (int i = 0; i < lobes.length; i++) {
                lobes[i] = StormCloudShape.lobe(i, lobes.length, 0, settings.seed,
                        settings.height, settings.cloudRadius, new Vector4f());
            }
            SurfaceProjector cloud = (x, y, z, out) ->
                    out.set(x, StormCloudShape.underside(x, z, lobes) - 0.07f, z);
            for (int layer = 0; layer < 2; layer++) {
                boolean sky = layer == 1;
                var surface = sky ? cloud : settings.ground;
                float reach = sky ? settings.cloudRadius : settings.groundRadius;
                int patches = sky ? settings.cloudArcs : settings.groundArcs;
                for (int patch = 0; patch < patches; patch++) {
                    long seed = settings.seed + 3001 + layer * 9001L + patch * 137L;
                    float angle = unit(seed, 0) * 6.283185f;
                    float cx;
                    float cz;
                    if (sky) {
                        var lobe = lobes[(patch * 7 + 3) % lobes.length];
                        cx = lobe.x;
                        cz = lobe.z;
                    } else {
                        float centerAngle = unit(seed, 4) * 6.283185f;
                        float distance = reach * (float) Math.sqrt(unit(seed, 5)) * 0.74f;
                        cx = (float) Math.cos(centerAngle) * distance;
                        cz = (float) Math.sin(centerAngle) * distance;
                    }
                    if (count == traces.size()) traces.add(new Trace());
                    var trace = traces.get(count++);
                    trace.path.clearPoints();
                    trace.sky = sky;
                    trace.seed = seed;
                    trace.brightness = 0.60f + unit(seed + settings.frame * 31L, 8) * 0.40f;
                    float length = reach * (0.22f + unit(seed, 2) * 0.18f);
                    append(trace.path, surface, cx, cz, angle, length, settings.width,
                            seed, settings.frame, sky, settings.segments, 0);
                    // Sparse local forks share a mesh with their parent, but never connect across strokes.
                    if (settings.forks && patch % 3 == 0 && trace.path.size() >= 4) {
                        int at = trace.path.size() / 2;
                        append(trace.path, surface, trace.path.x(at), trace.path.z(at), angle + 1.15f,
                                length * 0.48f, settings.width * 0.50f,
                                seed + 99, settings.frame, sky, settings.segments, 1);
                    }
                }
            }
        }
    }

    private static void append(ArcCurve arc, SurfaceProjector surface,
                               float cx, float cz, float angle, float length, float width,
                               long seed, int frame, boolean sky, int limit, int generation) {
        var point = new Vector3f();
        var previous = new Vector3f();
        int stroke = arc.size() == 0 ? 0 : arc.segment(arc.size() - 1) + 1;
        boolean hasPrevious = false;
        int segments = Math.clamp((int) Math.ceil(length * 3), 6, limit);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        for (int i = 0; i <= segments; i++) {
            float u = (float) i / segments;
            float bend = (float) Math.sin(u * 6.5f) * length * 0.24f
                    + (unit(seed + frame * 71L, i / 2) - 0.5f) * length * 0.11f * (float) Math.sin(u * Math.PI);
            float x = cx + cos * length * u - sin * bend;
            float z = cz + sin * length * u + cos * bend;
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
                    arc.addPoint(previous.x, point.y, previous.z, thickness, generation, stroke);
                } else {
                    arc.addPoint(point.x, previous.y, point.z, thickness, generation, stroke);
                }
            }
            arc.addPoint(point.x, point.y, point.z, thickness, generation, stroke);
            previous.set(point);
            hasPrevious = true;
        }
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
