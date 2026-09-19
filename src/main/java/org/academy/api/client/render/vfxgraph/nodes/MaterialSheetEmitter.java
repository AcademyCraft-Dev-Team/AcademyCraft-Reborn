package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;

/** Bounded sheets on a sampled surface. No integration, orbit, arc, or particle accumulation. */
public final class MaterialSheetEmitter {
    private MaterialSheetEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("style", 0), number("count", 6), number("size", 0.2f), number("opacity", 0.1f), number("lighting", 1),
                new PropertySpec("surface", "Surface sampler", ValueType.STRING, Value.string("surface"), Optional.empty()));
    }

    public static SimNode create(VfxBlock block) {
        int count = Math.clamp((int) property(block, "count", 6), 1, 48);
        int style = Math.clamp((int) property(block, "style", 0), 0, 6);
        float size = Math.clamp(property(block, "size", 0.2f), 0.01f, 1);
        float opacity = Math.clamp(property(block, "opacity", 0.1f), 0, 0.75f);
        String surface = block.properties().getOrDefault("surface", "surface");
        var owned = new HashMap<Float, Integer>();
        int[] indices = new int[count];
        var p = new Vector3f();
        var normal = new Vector3f();
        var camera = new Vector3f();
        var forward = new Vector3f();
        var identity = new Matrix4f();
        return (buffer, ctx) -> {
            Arrays.fill(indices, -1);
            for (int i = 0; i < buffer.count(); i++) {
                var ordinal = owned.get(buffer.seed(i));
                if (ordinal != null) indices[ordinal] = i;
            }
            owned.entrySet().removeIf(entry -> indices[entry.getValue()] < 0);
            int start = buffer.count();
            for (int i = 0; i < count; i++) {
                if (indices[i] >= 0) continue;
                indices[i] = buffer.spawn();
                owned.put(buffer.seed(indices[i]), i);
            }
            ctx.emitBatch(start, buffer.count());
            float time = ctx.paramFloat("time", -1);
            if (time < 0 || !Float.isFinite(time)) time = ctx.time();
            float progress = Math.clamp(ctx.paramFloat("progress", time / 0.6f), 0, 1);
            float width = Math.clamp(ctx.paramFloat("width", 0.6f), 0.05f, 32);
            float height = Math.clamp(ctx.paramFloat("height", 1.8f), 0.1f, 64);
            float range = Math.clamp(ctx.paramFloat("range", 12), 3, 16);
            float strength = Math.clamp(ctx.paramFloat("strength", 1), 0, 1);
            float light = Math.clamp(ctx.paramFloat("light", 1), 0.12f, 1);
            long seed = (long) ctx.paramFloat("seed", 42);
            var sampler = ctx.sampler(surface);
            camera.set(ctx.paramVec3("camera", 0, 0), ctx.paramVec3("camera", 1, 2), ctx.paramVec3("camera", 2, -8));
            forward.set(ctx.paramVec3("camera_forward", 0, 0), ctx.paramVec3("camera_forward", 1, 0), ctx.paramVec3("camera_forward", 2, 1));
            boolean firstPerson = ctx.paramFloat("view_first_person", 0) > 0.5f;
            for (int i = 0; i < count; i++) {
                long random = seed + i * 7919L;
                float u = 0.16f + unit(random, 1) * 0.68f, v = 0.16f + unit(random, 2) * 0.68f;
                boolean visible = true;
                if (style == 0) {
                    // Sparse lateral/upper media, never a line of particles from the actor to its target.
                    float z = 3 + unit(random, 3) * (range - 3);
                    p.set((i % 2 == 0 ? -1 : 1) * (1.7f + unit(random, 4) * 2.2f),
                            1.4f + unit(random, 5) * 1.8f, z);
                    normal.set(0.12f * (float) Math.sin(time + i), 0.3f, -1).normalize();
                    p.y += 0.06f * (float) Math.sin(time * 1.4 + i);
                } else if (style == 5) {
                    p.set(0, -0.18f, 0);
                    normal.set(0, 1, 0);
                } else if (sampler != null) {
                    visible = sampler.sample(i, u, v, p, normal);
                } else {
                    SurfaceSampler.box(i, u, v, new Vector3f(-width / 2, 0, -width / 2),
                            new Vector3f(width / 2, height, width / 2), identity, p, normal);
                }
                float phase = style == 0 || surface.equals("ground") ? fract(time * 0.34f + unit(random, 6)) : progress;
                if (style == 4) phase = Math.clamp((phase - 0.16f) / 0.84f, 0, 1);
                if (style == 6) phase = Math.clamp((phase - 0.10f) / 0.90f, 0, 1);
                float envelope = style == 5 ? 1 : smooth(phase / 0.14f) * smooth((1 - phase) / 0.42f);
                float sheetSize = (style == 5 ? size : size * (0.7f + unit(random, 7) * 0.6f))
                        * Math.clamp(ctx.paramFloat("sheet_scale", 1), 0.1f, 2);
                if (style == 3 || style == 4 || style == 6) {
                    // Only a real terminal event gets the larger peeling finish.
                    float finished = ctx.paramFloat("finished", 0);
                    p.fma(0.018f + phase * (0.15f + unit(random, 8) * 0.2f + finished * 0.08f), normal);
                    if (style == 4 || style == 6) {
                        p.y += phase * phase * 0.12f;
                        p.x += (unit(random, 11) - 0.5f) * phase * 0.12f;
                        p.z += (unit(random, 12) - 0.5f) * phase * 0.12f;
                        sheetSize *= style == 4 ? 0.65f + phase * 1.5f : 1 - phase * 0.8f;
                    } else sheetSize *= 1 - phase * 0.7f;
                } else p.fma(0.012f + (style == 2 ? (1 - phase) * 0.018f : 0), normal);
                float distance = p.distance(camera);
                float safety = style == 0 ? mediaVisibility(p, camera, forward, firstPerson) : smooth((distance - 0.24f) / 0.4f);
                // Keep repair feedback visible on hands, while the near-field medium never reaches the eye.
                float alpha = visible ? opacity * strength * envelope * safety : 0;
                if (style <= 1) {
                    float lighting = Math.clamp(property(block, "lighting", 1), 0, 1);
                    alpha *= 1 - lighting + lighting * (0.35f + light * 0.65f);
                }
                float incidence = Math.abs(normal.x * ctx.paramVec3("sun_direction", 0, 0.3f)
                        + normal.y * ctx.paramVec3("sun_direction", 1, 0.9f)
                        + normal.z * ctx.paramVec3("sun_direction", 2, 0.3f));
                float shade = (style <= 1 ? 0.7f + light * (0.15f + incidence * 0.15f) : 0.9f) + unit(random, 9) * 0.08f;
                int particle = indices[i];
                buffer.setPosition(particle, p.x, p.y, p.z);
                buffer.setVelocity(particle, normal.x, normal.y, normal.z);
                buffer.setSize(particle, sheetSize);
                buffer.setColor(particle, shade, shade, shade, alpha);
                buffer.setRotation(particle, style == 5 ? i * 1.5707963f : unit(random, 10) * 6.283185f);
                buffer.setAge(particle, phase);
                buffer.setLifetime(particle, 1);
                buffer.setLayer(particle, ParticleBuffer.layerByte(style == 0 || style == 4 || style == 6 ? "cloud" : "smoke"));
            }
        };
    }

    /** Attenuates the complete sheet footprint using a conservative near-camera margin. */
    public static float mediaVisibility(Vector3f point, Vector3f camera, Vector3f forward, boolean firstPerson) {
        var delta = new Vector3f(point).sub(camera);
        float distance = delta.length();
        float fade = smooth((distance - 1.0f) / 0.9f);
        if (firstPerson && distance > 0.001f && distance < 8) {
            float cosine = delta.div(distance).dot(forward);
            fade *= 1 - smooth((cosine - 0.961f) / (0.982f - 0.961f));
        }
        return fade;
    }

    private static float fract(float v) { return v - (float) Math.floor(v); }
    private static float smooth(float v) { v = Math.clamp(v, 0, 1); return v * v * (3 - 2 * v); }
    private static float property(VfxBlock b, String k, float d) {
        try { float n = Float.parseFloat(b.properties().getOrDefault(k, Float.toString(d))); return Float.isFinite(n) ? n : d; }
        catch (NumberFormatException ignored) { return d; }
    }
    private static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id, ValueType.FLOAT, Value.of(value), Optional.empty());
    }
}
