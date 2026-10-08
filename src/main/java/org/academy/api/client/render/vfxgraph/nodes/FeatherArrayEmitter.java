package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.registry.PropertySpec;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.FeatherArrayGeometry;
import org.academy.api.client.render.vfxgraph.shape.SurfaceDetailSelector;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

/** Bounded skeletal feather array. Animation and material parameters remain editable in the graph. */
public final class FeatherArrayEmitter {
    private FeatherArrayEmitter() { }

    public static List<PropertySpec> properties() {
        return List.of(number("pairs", 3), number("feathers", 14), number("coverts", 8),
                number("span", 3.6f), number("root_height", 1.25f), number("root_depth", -0.2f),
                number("spread", 1), number("flex", 0.1f), number("opacity", 0.9f),
                number("grounded", 1), number("minimum_height", 0.12f),
                new PropertySpec("surface_detail", "surface_detail", ValueType.INT, Value.of(0), Optional.empty()),
                number("block_penetration", 0), new PropertySpec("occlusion", "occlusion", ValueType.STRING,
                        Value.string("occlusion"), Optional.empty()),
                text("spread_curve"), text("attack_curve"), text("opacity_curve"));
    }

    public static SimNode create(VfxBlock block) {
        int pairs = Math.clamp((int) property(block, "pairs", 3), 1, 8);
        int primaries = Math.clamp((int) property(block, "feathers", 14), 4, 32);
        int coverts = Math.clamp((int) property(block, "coverts", 8), 0, 24);
        float rootHeightDefault = property(block, "root_height", 1.25f);
        float rootDepthDefault = property(block, "root_depth", -0.2f);
        var slots = new ShapeParticleSlots(pairs * 2 * (primaries + coverts));
        var root = new Vector3f();
        var axis = new Vector3f();
        var normal = new Vector3f();
        var camera = new Vector3f();
        var queryRoot = new Vector3f();
        var queryTip = new Vector3f();
        var clipPlanes = new Vector4f[pairs * 2];
        for (int i = 0; i < clipPlanes.length; i++) clipPlanes[i] = new Vector4f(0, 0, 0, 1);
        return (buffer, context) -> {
            slots.update(buffer, context);
            float time = live(context, "time", context.time());
            if (time < 0) time = context.time();
            float span = Math.clamp(live(context, "span", property(block, "span", 3.6f)), 0.1f, 32);
            float rootHeight = live(context, "root_height", rootHeightDefault);
            float rootDepth = live(context, "root_depth", rootDepthDefault);
            float spread = Math.clamp(live(context, "spread", property(block, "spread", 1)), 0, 1);
            float progress = Math.clamp(live(context, "progress", time), 0, 1);
            spread *= Math.clamp(curve(context, block, "spread_curve", progress, 1), 0, 1);
            float flexibility = Math.clamp(live(context, "flexibility", property(block, "flex", 0.1f)), 0, 0.6f);
            float attack = Math.clamp(live(context, "attack", 0), 0, 1);
            attack = Math.clamp(curve(context, block, "attack_curve", progress, attack), 0, 1);
            float density = Math.clamp(live(context, "density", 0.65f), 0, 1);
            float grounded = Math.clamp(live(context, "grounded", property(block, "grounded", 1)), 0, 1);
            float minimumHeight = live(context, "minimum_height", property(block, "minimum_height", 0.12f));
            float edgeActivity = Math.clamp(live(context, "edge_activity", live(context, "edgeActivity", 0.2f)), 0, 1);
            float penetration = Math.clamp(live(context, "block_penetration", property(block, "block_penetration", 0)), 0, 1);
            var occlusion = penetration < 1 ? context.occlusionSampler(block.properties().getOrDefault("occlusion", "occlusion")) : null;
            float opacity = Math.clamp(live(context, "strength", 1) * property(block, "opacity", 0.9f), 0, 1);
            opacity *= Math.clamp(curve(context, block, "opacity_curve", progress, 1), 0, 1);
            float lod = Math.clamp(live(context, "lod", 1), 0.2f, 1);
            var detailParameter = context.param("surface_detail");
            int surfaceDetail = SurfaceDetailSelector.validDetail(detailParameter != null && detailParameter.type() == ValueType.INT
                    ? detailParameter.asInt() : Math.round(live(context, "surface_detail", property(block, "surface_detail", 0))));
            camera.set(context.paramVec3("camera", 0, 0), context.paramVec3("camera", 1, 2), context.paramVec3("camera", 2, -8));
            boolean firstPerson = live(context, "view_first_person", 0) > 0.5f;
            int ordinal = 0;
            for (int pair = 0; pair < pairs; pair++) {
                for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                    int side = sideIndex == 0 ? -1 : 1;
                    var clipPlane = clipPlanes[pair * 2 + sideIndex].set(0, 0, 0, 1);
                    if (occlusion != null && opacity > 0) {
                        float widthRatio = 0.24f * (0.85f + density * 0.2f);
                        FeatherArrayGeometry.sample(pair, pairs, side, 0, primaries, false, span,
                                rootHeight, rootDepth,
                                spread, time, flexibility, attack, grounded, minimumHeight, widthRatio,
                                0, edgeActivity, queryRoot, axis, normal);
                        FeatherArrayGeometry.sample(pair, pairs, side, primaries - 1, primaries, false, span,
                                rootHeight, rootDepth,
                                spread, time, flexibility, attack, grounded, minimumHeight, widthRatio,
                                0, edgeActivity, root, axis, normal);
                        queryTip.set(root).add(axis);
                        float halfWidth = axis.length() * widthRatio * 0.5f;
                        if (!occlusion.sample(pair * 2 + sideIndex, queryRoot, queryTip, normal, halfWidth, clipPlane)
                                || !clipPlane.isFinite()) clipPlane.set(0, 0, 0, 1);
                    }
                    for (int layer = 0; layer < 2; layer++) {
                        boolean covert = layer == 1;
                        int count = covert ? coverts : primaries;
                        for (int feather = 0; feather < count; feather++) {
                            int particle = slots.index(ordinal++);
                            buffer.setSurfaceDetail(particle, surfaceDetail);
                            buffer.setLayer(particle, ParticleBuffer.layerByte("feather"));
                            buffer.setAge(particle, edgeActivity);
                            buffer.setLifetime(particle, 1);
                            // Retain ownership and lifetime while omitting redundant hidden geometry.
                            if (opacity <= 0 || covert && feather >= Math.ceil(count * lod)) {
                                buffer.setAlpha(particle, 0);
                                continue;
                            }
                            float widthRatio = (covert ? 0.38f : 0.24f) * (0.85f + density * 0.2f);
                            float roll = flexibility * 0.12f * (float) Math.sin(time * 1.8f + feather * 0.3f);
                            FeatherArrayGeometry.sample(pair, pairs, side, feather, count, covert, span,
                                    rootHeight, rootDepth,
                                    spread, time, flexibility, attack, grounded, minimumHeight, widthRatio,
                                    roll, edgeActivity, root, axis, normal);
                            float near = firstPerson ? nearVisibility(root, axis, camera, axis.length() * widthRatio * 0.5f) : 1;
                            float shade = covert ? 0.85f + feather / (float) Math.max(1, count) * 0.07f : 0.95f;
                            buffer.setPosition(particle, root.x, root.y, root.z);
                            buffer.setVelocity(particle, axis.x, axis.y, axis.z);
                            buffer.setSurfaceNormal(particle, normal.x, normal.y, normal.z);
                            buffer.setSurfaceClipPlane(particle, clipPlane.x, clipPlane.y, clipPlane.z, clipPlane.w);
                            buffer.setSurfacePenetration(particle, penetration);
                            buffer.setSize(particle, widthRatio);
                            buffer.setRotation(particle, roll);
                            buffer.setColor(particle, shade, shade * 0.995f, shade * 0.985f, opacity * near);
                        }
                    }
                }
            }
        };
    }

    /** Conservative first-person fade using distance to the complete feather spine segment. */
    public static float nearVisibility(Vector3f root, Vector3f axis, Vector3f camera) {
        return nearVisibility(root, axis, camera, 0);
    }

    public static float nearVisibility(Vector3f root, Vector3f axis, Vector3f camera, float halfWidth) {
        float x = camera.x - root.x, y = camera.y - root.y, z = camera.z - root.z;
        float t = axis.lengthSquared() > 0.000001f
                ? Math.clamp((x * axis.x + y * axis.y + z * axis.z) / axis.lengthSquared(), 0, 1) : 0;
        x -= axis.x * t; y -= axis.y * t; z -= axis.z * t;
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        return smooth((distance - Math.max(0, halfWidth) - 0.65f) / 0.55f);
    }

    static float live(SimContext context, String key, float fallback) {
        float value = context.paramFloat(key, fallback);
        return Float.isFinite(value) ? value : fallback;
    }

    static float property(VfxBlock block, String key, float fallback) {
        try {
            float value = Float.parseFloat(block.properties().getOrDefault(key, Float.toString(fallback)));
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }

    static float smooth(float value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    static PropertySpec number(String id, float value) {
        return new PropertySpec(id, id, ValueType.FLOAT, Value.of(value), Optional.empty());
    }

    static PropertySpec text(String id) {
        return new PropertySpec(id, id, ValueType.STRING, Value.string(""), Optional.empty());
    }

    static float curve(SimContext context, VfxBlock block, String property, float progress, float fallback) {
        var curve = context.curve(block.properties().getOrDefault(property, ""));
        if (curve == null) return fallback;
        float value = CurveSampler.sample(curve, progress);
        return Float.isFinite(value) ? value : fallback;
    }
}
