package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.SurfaceDetailSelector;
import org.academy.api.client.render.vfxgraph.shape.SurfaceLaminaGeometry;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

import static org.academy.api.client.render.vfxgraph.shape.SkyDischargeGeometry.unit;
import static org.academy.api.client.render.vfxgraph.shape.SurfaceLaminaGeometry.smooth;

/**
 * Bounded connected surface layers: outward delamination or opposing inward reconstruction.
 * Callers bind an effect-local {@link SurfaceSampler}: each index identifies one stable patch,
 * with continuous u/v coordinates and an outward normal. Returning false hides that patch.
 * {@code surface_patches} selects the patch count, while {@code repair_progress_0..5} can drive
 * independent sites; negative/missing site progress uses the common {@code progress} parameter.
 * The emitter retains fixed particle slots, so sustained pulses and non-player consumers need
 * only update their surface/pose and progress, without recreating the graph or allocating bursts.
 */
public final class SurfaceLaminaEmitter {
    private static final byte LAYER = ParticleBuffer.layerByte("lamina");
    private static final String[] PART_PROGRESS = {"repair_progress_0", "repair_progress_1", "repair_progress_2",
            "repair_progress_3", "repair_progress_4", "repair_progress_5"};

    private SurfaceLaminaEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(text("mode", "disassemble"), text("surface", "surface"),
                number("patches", 6), number("strands", 8), number("fragments", 12),
                number("opacity", 0.96f), number("length_scale", 1), number("width_scale", 1), number("lift", 0.16f));
    }

    public static SimNode create(VfxBlock block) {
        boolean repair = "repair".equals(block.properties().getOrDefault("mode", "disassemble"));
        int patches = Math.clamp((int) property(block, "patches", 6), 1, 6);
        int configuredStrands = Math.clamp((int) property(block, "strands", repair ? 4 : 8), 2, 12);
        int strands = repair ? configuredStrands / 2 * 2 : configuredStrands;
        int fragments = repair ? 0 : Math.clamp((int) property(block, "fragments", 12), 0, 24);
        float opacity = Math.clamp(property(block, "opacity", 0.96f), 0, 1);
        float lengthScale = Math.clamp(property(block, "length_scale", 1), 0.2f, 1.25f);
        float widthScale = Math.clamp(property(block, "width_scale", 1), 0.2f, 1.5f);
        float lift = Math.clamp(property(block, "lift", 0.16f), 0.001f, 0.5f);
        String surface = block.properties().getOrDefault("surface", "surface");
        var slots = new ShapeParticleSlots(patches * strands + fragments);
        var root = new Vector3f();
        var tip = new Vector3f();
        var acrossA = new Vector3f();
        var acrossB = new Vector3f();
        var normal = new Vector3f();
        var scratchNormal = new Vector3f();
        var axis = new Vector3f();
        var side = new Vector3f();
        var fragmentPoint = new Vector3f();
        var camera = new Vector3f();
        var min = new Vector3f();
        var max = new Vector3f();
        var identity = new Matrix4f();
        var origins = new Vector3f[patches * strands];
        var axes = new Vector3f[origins.length];
        var normals = new Vector3f[origins.length];
        var valid = new boolean[origins.length];
        for (int i = 0; i < origins.length; i++) {
            origins[i] = new Vector3f(); axes[i] = new Vector3f(); normals[i] = new Vector3f();
        }
        return (buffer, context) -> {
            slots.update(buffer, context);
            float time = live(context, "time", context.time());
            if (time < 0) time = context.time();
            float globalProgress = Math.clamp(live(context, "progress", time / (repair ? 0.38f : 0.65f)), 0, 1);
            float strength = Math.clamp(live(context, "strength", 1), 0, 1);
            float light = Math.clamp(live(context, "light", 1), 0, 1);
            float density = Math.clamp(live(context, "density", 0.5f), 0, 1);
            float flexibility = Math.clamp(live(context, "flexibility", 0.5f), 0, 1);
            float lod = Math.clamp(live(context, "lod", 1), 0.15f, 1);
            var sampler = context.sampler(surface);
            // The editor's fallback is one front face, not six coincident body-part surfaces.
            int activePatches = sampler == null ? 1
                    : Math.clamp(Math.round(live(context, "surface_patches", repair ? patches : 1)), 1, patches);
            int activeStrands = repair ? strands : Math.max(2, strands / activePatches);
            int layoutStrands = activeStrands;
            // Reduce complete joined pairs; never drop just one half of a repair bridge.
            if (repair) activeStrands = Math.max(2, ((int) Math.ceil(activeStrands * lod / 2)) * 2);
            else activeStrands = Math.max(2, (int) Math.ceil(activeStrands * lod));
            activeStrands = Math.min(activeStrands, strands);
            var detailValue = context.param("surface_detail");
            int detail = detailValue != null && detailValue.type() == ValueType.INT
                    ? SurfaceDetailSelector.validDetail(detailValue.asInt()) : SurfaceDetailSelector.FULL;
            float width = Math.clamp(live(context, "width", 0.6f), 0.05f, 16);
            float height = Math.clamp(live(context, "height", 1.8f), 0.05f, 32);
            min.set(-width / 2, 0, -width / 2); max.set(width / 2, height, width / 2);
            camera.set(context.paramVec3("camera", 0, 0), context.paramVec3("camera", 1, 2), context.paramVec3("camera", 2, -8));
            long seed = (long) live(context, "seed", 42);
            for (int patch = 0; patch < patches; patch++) {
                float partProgress = repair ? live(context, PART_PROGRESS[patch], -1) : -1;
                float progress = partProgress >= 0 ? Math.clamp(partProgress, 0, 1) : globalProgress;
                float envelope = smooth(progress / 0.06f) * smooth((1 - progress) / (repair ? 0.2f : 0.28f));
                for (int strand = 0; strand < strands; strand++) {
                    int ordinal = patch * strands + strand;
                    int particle = slots.index(ordinal);
                    buffer.setAlpha(particle, 0);
                    valid[ordinal] = false;
                    if (patch >= activePatches || strand >= activeStrands || envelope * strength <= 0) continue;
                    long random = seed + patch * 3571L + strand * 7919L;
                    int column = repair ? strand / 2 : strand;
                    int columns = repair ? (layoutStrands + 1) / 2 : layoutStrands;
                    float station = (column + 0.5f) / columns;
                    float u = repair ? 0.16f + station * 0.68f : 0.28f + station * 0.44f;
                    float inset = 0.06f + unit(random, 9) * (repair ? 0.12f : 0.24f);
                    float rootV = repair && strand % 2 == 1 ? 1 - inset : inset;
                    float tipV = repair ? (strand % 2 == 1 ? 0.42f : 0.58f) : 0.76f + unit(random, 1) * 0.14f;
                    tipV = rootV + (tipV - rootV) * lengthScale;
                    float tipU = repair ? u + (0.5f - station) * 0.40f : 0.04f + station * 0.92f;
                    float du = 0.68f / columns * 0.67f * widthScale * (0.92f + density * 0.16f);
                    boolean sampled = sample(sampler, patch, u, rootV, min, max, identity, root, normal)
                            && sample(sampler, patch, tipU, tipV, min, max, identity, tip, scratchNormal)
                            && sample(sampler, patch, u - du, rootV, min, max, identity, acrossA, scratchNormal)
                            && sample(sampler, patch, u + du, rootV, min, max, identity, acrossB, scratchNormal);
                    if (!sampled || !root.isFinite() || !tip.isFinite() || !normal.isFinite() || normal.lengthSquared() < 0.00001f) continue;
                    normal.normalize();
                    axis.set(tip).sub(root);
                    float length = axis.length();
                    float layerWidth = acrossA.distance(acrossB);
                    if (!Float.isFinite(length) || !Float.isFinite(layerWidth) || length < 0.002f || layerWidth < 0.001f) continue;
                    root.fma(0.007f, normal);
                    origins[ordinal].set(root); axes[ordinal].set(axis); normals[ordinal].set(normal); valid[ordinal] = true;
                    float proximity = smooth((camera.distance(root) - 0.1f) / 0.16f);
                    float shade = (0.26f + light * 0.71f) * (0.97f + unit(random, 3) * 0.03f);
                    write(buffer, particle, root, axis, normal, layerWidth / length,
                            SurfaceLaminaGeometry.encodeMotion(repair ? SurfaceLaminaGeometry.Motion.REPAIR : SurfaceLaminaGeometry.Motion.DISASSEMBLE,
                                    lift * (0.64f + unit(random, 10) * 0.60f) * (0.8f + flexibility * 0.4f)),
                            progress, shade, opacity * envelope * strength * proximity, detail);
                }
            }
            for (int fragment = 0; fragment < fragments; fragment++) {
                int particle = slots.index(patches * strands + fragment);
                buffer.setAlpha(particle, 0);
                if (fragment >= Math.ceil(fragments * lod)) continue;
                int sourcePatch = fragment % activePatches;
                int sourceStrand = (fragment / activePatches) % activeStrands;
                int source = sourcePatch * strands + sourceStrand;
                if (!valid[source]) continue;
                long random = seed + fragment * 1217L;
                float start = 0.49f + unit(random, 4) * 0.12f;
                float flight = Math.clamp((globalProgress - start) / (1 - start), 0, 1);
                float alpha = smooth(flight / 0.12f) * smooth((1 - flight) / 0.45f) * opacity * strength;
                if (alpha <= 0) continue;
                normal.set(normals[source]);
                int sourceParticle = slots.index(source);
                axis.set(axes[source]);
                float sourceLength = axis.length();
                axis.div(sourceLength);
                side.set(axis).cross(normal).normalize();
                SurfaceLaminaGeometry.sample(0.75f, 0.74f + unit(random, 5) * 0.22f,
                        sourceLength, buffer.size(sourceParticle), buffer.rotation(sourceParticle), start,
                        SurfaceLaminaGeometry.Motion.DISASSEMBLE, buffer.seed(sourceParticle) * 0.6180339f, fragmentPoint);
                root.set(origins[source]).fma(fragmentPoint.x, side).fma(fragmentPoint.y, axis).fma(fragmentPoint.z, normal);
                root.fma(flight * (0.10f + unit(random, 6) * 0.16f), normal);
                root.y += flight * flight * 0.06f;
                axis.set(axes[source]).normalize().mul((0.032f + unit(random, 7) * 0.042f) * (1 - flight * 0.65f));
                write(buffer, particle, root, axis, normal, 0.32f + unit(random, 8) * 0.22f,
                        SurfaceLaminaGeometry.encodeMotion(SurfaceLaminaGeometry.Motion.FRAGMENT, 0.012f),
                        flight, 0.27f + light * 0.7f, alpha, SurfaceDetailSelector.FAR);
            }
        };
    }

    private static boolean sample(SurfaceSampler sampler, int patch, float u, float v, Vector3f min, Vector3f max,
                                  Matrix4f identity, Vector3f point, Vector3f normal) {
        u = Math.clamp(u, 0.015f, 0.985f); v = Math.clamp(v, 0.015f, 0.985f);
        if (sampler != null) return sampler.sample(patch, u, v, point, normal);
        SurfaceSampler.box(4, u, v, min, max, identity, point, normal);
        return true;
    }

    private static void write(ParticleBuffer buffer, int index, Vector3f root, Vector3f axis, Vector3f normal,
                              float width, float motion, float progress, float shade, float opacity, int detail) {
        buffer.setPosition(index, root.x, root.y, root.z);
        buffer.setVelocity(index, axis.x, axis.y, axis.z);
        buffer.setSurfaceNormal(index, normal.x, normal.y, normal.z);
        buffer.setSurfaceClipPlane(index, 0, 0, 0, 1);
        buffer.setSurfacePenetration(index, 0);
        buffer.setSurfaceDetail(index, detail);
        buffer.setSize(index, width);
        buffer.setRotation(index, motion);
        buffer.setColor(index, shade, shade * 0.998f, shade * 0.99f, opacity);
        buffer.setAge(index, progress);
        buffer.setLifetime(index, 1);
        buffer.setLayer(index, LAYER);
    }

    private static float live(SimContext context, String name, float fallback) {
        float value = context.paramFloat(name, fallback);
        return Float.isFinite(value) ? value : fallback;
    }

    private static float property(VfxBlock block, String key, float fallback) {
        try { float value = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback))); return Float.isFinite(value) ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static PropertySpec number(String id, float value) { return new PropertySpec(id, id, ValueType.FLOAT, Value.of(value), Optional.empty()); }
    private static PropertySpec text(String id, String value) { return new PropertySpec(id, id, ValueType.STRING, Value.string(value), Optional.empty()); }
}
