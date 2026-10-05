package org.academy.api.client.render.vfxgraph.nodes;

import net.minecraft.util.Mth;
import org.academy.api.client.render.vfxgraph.arc.CurveGenerator;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class ArcEmitterBlocks {
    private ArcEmitterBlocks() {
    }

    static SimNode bolt(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var ox = BlockProps.getFloat(properties, "origin_x", 0f);
        var oy = BlockProps.getFloat(properties, "origin_y", 0f);
        var oz = BlockProps.getFloat(properties, "origin_z", 0f);
        var fromX = BlockProps.getFloat(properties, "from_x", ox);
        var fromY = BlockProps.getFloat(properties, "from_y", oy);
        var fromZ = BlockProps.getFloat(properties, "from_z", oz);
        var toX = BlockProps.getFloat(properties, "to_x", ox);
        var toY = BlockProps.getFloat(properties, "to_y", oy + 2f);
        var toZ = BlockProps.getFloat(properties, "to_z", oz);
        var probability = BlockProps.getFloat(properties, "probability", 0.5f);
        var width = BlockProps.getFloat(properties, "width", 0.01f);
        var segments = BlockProps.getInt(properties, "segments", 12);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var color = BlockProps.getColor(properties, "color");
        var emission = BlockProps.getFloat(properties, "emission", 1f);
        var branchDepth = BlockProps.getInt(properties, "branch_depth", 1);
        var branchCount = BlockProps.getInt(properties, "branch_count", 2);
        var branchAngle = BlockProps.getFloat(properties, "branch_angle", 1.57f);
        var branchLengthScale = BlockProps.getFloat(properties, "branch_length_scale", 0.3f);
        var branchLengthScaleParam = BlockProps.getString(properties, "branch_length_scale_param", "");
        var branchWidthScale = BlockProps.getFloat(properties, "branch_width_scale", 0.35f);
        var branchBrightnessScale = BlockProps.getFloat(properties, "branch_brightness_scale", 0.6f);
        float vx = toX - fromX, vy = toY - fromY, vz = toZ - fromZ;
        var vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float nx, ny, nz;
        if (vlen < 1e-6f) {
            nx = 0;
            ny = 1;
            nz = 0;
        } else {
            nx = vx / vlen;
            ny = vy / vlen;
            nz = vz / vlen;
        }
        long[] seed = {0L};
        var interval = BlockProps.getFloat(properties, "interval", 0f);
        float[] accumulator = {0f};
        var reshuffle = BlockProps.getFloat(properties, "reshuffle", 0f);
        long[] transientGroup = {ArcSupport.newTransientGroup()};
        float[] carryAge = {0f};
        return (buf, ctx) -> {
            var bls = branchLengthScaleParam.isEmpty()
                    ? branchLengthScale
                    : ctx.paramFloat(branchLengthScaleParam, branchLengthScale);
            if (reshuffle > 0f) {
                accumulator[0] += ctx.dt();
                if (accumulator[0] < reshuffle) return;
                accumulator[0] = 0f;
                if (carryAge[0] >= 0f && carryAge[0] < lifetime) {
                    carryAge[0] += reshuffle;
                    if (carryAge[0] >= lifetime) {
                        ctx.arcs().removeGroup(transientGroup[0]);
                        carryAge[0] = Float.NaN;
                    } else {
                        ctx.arcs().removeGroup(transientGroup[0]);
                        seed[0]++;
                        var arc = ctx.arcs().add(transientGroup[0]);
                        arc.setAge(carryAge[0]);
                        CurveGenerator.generateFromTo(
                                arc, fromX, fromY, fromZ, toX, toY, toZ,
                                nx, ny, nz,
                                width, segments,
                                color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                                lifetime, seed[0],
                                branchDepth, branchCount, branchAngle,
                                bls, branchWidthScale, branchBrightnessScale);
                    }
                } else {
                    if (ctx.random().nextFloat() > probability) return;
                    ctx.arcs().removeGroup(transientGroup[0]);
                    carryAge[0] = 0f;
                    seed[0]++;
                    var arc = ctx.arcs().add(transientGroup[0]);
                    arc.setAge(0f);
                    CurveGenerator.generateFromTo(
                            arc, fromX, fromY, fromZ, toX, toY, toZ,
                            nx, ny, nz,
                            width, segments,
                            color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                            lifetime, seed[0],
                            branchDepth, branchCount, branchAngle,
                            bls, branchWidthScale, branchBrightnessScale);
                }
                return;
            }
            if (interval > 0f) {
                accumulator[0] += ctx.dt();
                if (accumulator[0] < interval) return;
                accumulator[0] = 0f;
            }
            seed[0]++;
            if (ctx.random().nextFloat() > probability) return;
            var arc = ctx.arcs().add();
            CurveGenerator.generateFromTo(
                    arc, fromX, fromY, fromZ, toX, toY, toZ,
                    nx, ny, nz,
                    width, segments,
                    color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                    lifetime, seed[0],
                    branchDepth, branchCount, branchAngle,
                    bls, branchWidthScale, branchBrightnessScale);
        };
    }

    static SimNode orbit(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var ox = BlockProps.getFloat(properties, "origin_x", 0f);
        var oy = BlockProps.getFloat(properties, "origin_y", 0f);
        var oz = BlockProps.getFloat(properties, "origin_z", 0f);
        var radius = BlockProps.getFloat(properties, "radius", 1.5f);
        var width = BlockProps.getFloat(properties, "width", 0.01f);
        var segments = BlockProps.getInt(properties, "segments", 12);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 0f);
        var color = BlockProps.getColor(properties, "color");
        var emission = BlockProps.getFloat(properties, "emission", 1f);
        var height = BlockProps.getFloat(properties, "height", 2f);
        var branchDepth = BlockProps.getInt(properties, "branch_depth", 0);
        var branchCount = BlockProps.getInt(properties, "branch_count", 2);
        var branchAngle = BlockProps.getFloat(properties, "branch_angle", 1.57f);
        var branchLengthScale = BlockProps.getFloat(properties, "branch_length_scale", 0.3f);
        var branchWidthScale = BlockProps.getFloat(properties, "branch_width_scale", 0.35f);
        var branchBrightnessScale = BlockProps.getFloat(properties, "branch_brightness_scale", 0.6f);
        long[] seed = {0L};
        float[] angle = {0f};
        var speed = BlockProps.getFloat(properties, "speed", 1f);
        return (buf, ctx) -> {
            seed[0]++;
            angle[0] += ctx.dt() * speed;
            var x = ox + (float) Math.cos(angle[0]) * radius;
            var z = oz + (float) Math.sin(angle[0]) * radius;
            var arc = ctx.arcs().add();
            CurveGenerator.generate(
                    arc, x, oy, z, 0, 1, 0,
                    width, segments,
                    color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                    lifetime, seed[0],
                    branchDepth, branchCount, branchAngle,
                    branchLengthScale, branchWidthScale, branchBrightnessScale, height);
        };
    }

    static SimNode shockwave(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var duration = BlockProps.getFloat(properties, "duration", 1.25f);
        var baseRadius = BlockProps.getFloat(properties, "base_radius", 1.5f);
        var maxRadius = BlockProps.getFloat(properties, "max_radius", 28f);
        int ringCount = Math.max(1, BlockProps.getInt(properties, "ring_count", 5));
        int segments = Math.max(16, BlockProps.getInt(properties, "segments", 80));
        var width = BlockProps.getFloat(properties, "width", 0.085f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 0.075f);
        var color = BlockProps.getColor(properties, "color");
        long[] seed = {0L};
        long group = ArcSupport.newTransientGroup();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(group);
            var time = ctx.paramFloat("time", ctx.time());
            if (time < 0) time = ctx.time();
            if (time > duration) return;
            for (int ring = 0; ring < ringCount; ring++) {
                float delay = ring * 0.075f;
                float t = BlockSim.clamp01((time / duration - delay) / Math.max(0.05f, 1f - delay));
                if (t <= 0f || t >= 0.999f) continue;
                float eased = 1f - (float) Math.pow(1f - t, 3f);
                float radius = BlockSim.lerp(baseRadius, maxRadius * (1f - ring * 0.055f), eased);
                float alpha = (1f - BlockSim.smoothstep(t)) * (1f - ring * 0.08f);
                float tilt = (ring - (ringCount - 1) * 0.5f) * 0.11f;
                var arc = ctx.arcs().add(group);
                for (int point = 0; point <= segments; point++) {
                    float angle = (float) (Math.PI * 2.0 * point / segments) + ring * 0.41f;
                    float x = (float) Math.cos(angle) * radius;
                    float z = (float) Math.sin(angle) * radius;
                    float y = z * (float) Math.sin(tilt);
                    z *= (float) Math.cos(tilt);
                    arc.addPoint(x, y, z, width * (1f + (1f - t) * 0.8f), 0f);
                }
                ArcSupport.configure(arc, color, alpha, lifetime, ++seed[0]);
            }
        };
    }

    static SimNode radialRipple(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var duration = Math.max(0.001f, BlockProps.getFloat(properties, "duration", 1f));
        String durationParam = BlockProps.getString(properties, "duration_param", "");
        var intensity = Math.max(0f, BlockProps.getFloat(properties, "intensity", 1f));
        String intensityParam = BlockProps.getString(properties, "intensity_param", "");
        var maxRadius = Math.max(0f, BlockProps.getFloat(properties, "radius", 1.5f));
        int ringCount = Math.max(1, BlockProps.getInt(properties, "ring_count", 8));
        int segments = Math.max(16, BlockProps.getInt(properties, "segments", 32));
        var widthScale = Math.max(0.01f, BlockProps.getFloat(properties, "width_scale", 0.48f));
        var lifetime = Math.max(0.001f, BlockProps.getFloat(properties, "lifetime", 0.075f));
        var core = BlockProps.getColor(properties, "core_color");
        String coreParam = BlockProps.getString(properties, "core_color_param", "");
        var edge = BlockProps.getColor(properties, "edge_color");
        String edgeParam = BlockProps.getString(properties, "edge_color_param", "");
        long[] seed = {0L};
        return (buf, ctx) -> {
            var liveDuration = Math.max(0.001f, ctx.paramFloat(durationParam, duration));
            var t = ctx.time() / liveDuration;
            if (t < 0f || t >= 1f) return;
            var liveIntensity = Math.max(0f, ctx.paramFloat(intensityParam, intensity));
            var envelope = 1f - Math.abs(t * 2f - 1f);
            if (envelope <= 1e-4f || liveIntensity <= 1e-4f) return;

            var radius = maxRadius * envelope;
            var halfWidth = Math.max(0.0025f, radius / ringCount * widthScale);
            var coreR = ctx.paramColor(coreParam, 0, core[0]);
            var coreG = ctx.paramColor(coreParam, 1, core[1]);
            var coreB = ctx.paramColor(coreParam, 2, core[2]);
            var coreA = ctx.paramColor(coreParam, 3, core[3]);
            var edgeR = ctx.paramColor(edgeParam, 0, edge[0]);
            var edgeG = ctx.paramColor(edgeParam, 1, edge[1]);
            var edgeB = ctx.paramColor(edgeParam, 2, edge[2]);
            var edgeA = ctx.paramColor(edgeParam, 3, edge[3]);
            var alphaEnvelope = 0.8f * liveIntensity * envelope;

            for (int ring = 0; ring < ringCount; ring++) {
                float ringT = (ring + 0.5f) / ringCount;
                float ringRadius = radius * ringT;
                var arc = ctx.arcs().add();
                for (int point = 0; point <= segments; point++) {
                    float angle = Mth.TWO_PI * point / segments;
                    arc.addPoint(
                            Mth.cos(angle) * ringRadius,
                            0f,
                            Mth.sin(angle) * ringRadius,
                            halfWidth,
                            0f);
                }
                arc.setColor(
                        BlockSim.lerp(coreR, edgeR, ringT),
                        BlockSim.lerp(coreG, edgeG, ringT),
                        BlockSim.lerp(coreB, edgeB, ringT),
                        BlockSim.clamp01(BlockSim.lerp(coreA, edgeA, ringT) * alphaEnvelope));
                arc.setLifetime(lifetime);
                arc.setSeed(++seed[0]);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }

    static SimNode collapsingBox(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        String progressParam = BlockProps.getString(properties, "progress_param", "progress");
        String widthParam = BlockProps.getString(properties, "width_param", "width");
        String heightParam = BlockProps.getString(properties, "height_param", "height");
        String yawParam = BlockProps.getString(properties, "yaw_param", "yaw");
        var defaultWidth = Math.max(0.3f, BlockProps.getFloat(properties, "width", 1f));
        var defaultHeight = Math.max(0.5f, BlockProps.getFloat(properties, "height", 2f));
        var defaultYaw = BlockProps.getFloat(properties, "yaw", 0f);
        var collapseDegrees = BlockProps.getFloat(properties, "collapse_degrees", 81f);
        var lineWidth = Math.max(0.001f, BlockProps.getFloat(properties, "line_width", 0.02f));
        var lifetime = Math.max(0.001f, BlockProps.getFloat(properties, "lifetime", 0.075f));
        var color = BlockProps.getColor(properties, "color");
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        long[] seed = {0L};
        return (buf, ctx) -> {
            float progress = BlockSim.clamp01(ctx.paramFloat(progressParam, 0f));
            if (progress >= 1f) return;
            float width = Math.max(0.3f, ctx.paramFloat(widthParam, defaultWidth));
            float height = Math.max(0.5f, ctx.paramFloat(heightParam, defaultHeight));
            float entityYaw = ctx.paramFloat(yawParam, defaultYaw);
            float collapseProgress = BlockSim.clamp01(progress / 0.72f);
            float inverse = 1f - collapseProgress;
            float pitch = collapseDegrees * (1f - inverse * inverse * inverse) * Mth.DEG_TO_RAD;
            float yaw = (180f - entityYaw) * Mth.DEG_TO_RAD;
            float cosY = Mth.cos(yaw);
            float sinY = Mth.sin(yaw);
            float cosX = Mth.cos(pitch);
            float sinX = Mth.sin(pitch);
            float half = Math.max(0.15f, width * 0.5f);
            float[][] vertices = {
                    {-half, 0f, -half}, {half, 0f, -half}, {half, 0f, half}, {-half, 0f, half},
                    {-half, height, -half}, {half, height, -half}, {half, height, half}, {-half, height, half}
            };
            for (var vertex : vertices) {
                float x = vertex[0];
                float y = vertex[1];
                float z = vertex[2];
                float rotatedX = x * cosY - z * sinY;
                float rotatedZ = x * sinY + z * cosY;
                vertex[0] = rotatedX;
                vertex[1] = y * cosX - rotatedZ * sinX;
                vertex[2] = y * sinX + rotatedZ * cosX;
            }

            float flash = progress < 0.08f ? 1f - progress / 0.08f : 0f;
            float alpha = progress > 0.7f ? BlockSim.clamp01(1f - (progress - 0.7f) / 0.3f) : 1f;
            float red = BlockSim.clamp01(color[0] + (1f - color[0]) * flash);
            float green = BlockSim.clamp01(color[1] + (1f - color[1]) * flash);
            for (var edge : edges) {
                var from = vertices[edge[0]];
                var to = vertices[edge[1]];
                var arc = ctx.arcs().add();
                arc.addPoint(from[0], from[1], from[2], lineWidth, 0f);
                arc.addPoint(to[0], to[1], to[2], lineWidth, 0f);
                arc.setColor(red, green, color[2], color[3] * alpha);
                arc.setLifetime(lifetime);
                arc.setSeed(++seed[0]);
                arc.setNoiseStrength(0f);
                arc.setDriftSpeed(0f);
            }
        };
    }
}
