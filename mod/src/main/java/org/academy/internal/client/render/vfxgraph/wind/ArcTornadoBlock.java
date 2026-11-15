package org.academy.internal.client.render.vfxgraph.wind;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.BlockProps;
import org.academy.api.client.render.vfxgraph.nodes.BlockSim;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.academy.internal.client.render.vfxgraph.arc.ArcSupport;

final class ArcTornadoBlock {
    private ArcTornadoBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var expandParam = BlockProps.getString(properties, "expand_param", "expand_rate");
        var chargeParam = BlockProps.getString(properties, "charge_param", "charge_progress");
        var emissionParam = BlockProps.getString(properties, "emission_param", "emission");
        var focusParam = BlockProps.getString(properties, "focus_param", "focus_offset");
        var expandMin = BlockProps.getFloat(properties, "expand_min", 0.5f);
        var expandMax = Math.max(expandMin + 0.001f, BlockProps.getFloat(properties, "expand_max", 3f));
        var bottomRadius = Math.max(0.1f, BlockProps.getFloat(properties, "bottom_radius", 15f));
        var baseRadius = BlockProps.getFloat(properties, "base_radius", 15f);
        var maxRadius = BlockProps.getFloat(properties, "max_radius", 96f);
        var baseHeight = BlockProps.getFloat(properties, "base_height", 32f);
        var maxHeight = BlockProps.getFloat(properties, "max_height", 128f);
        var minRings = BlockProps.getInt(properties, "min_rings", 9);
        var maxRings = BlockProps.getInt(properties, "max_rings", 30);
        var minHelices = Math.max(0, BlockProps.getInt(properties, "min_helices", 1));
        var maxHelices = Math.max(minHelices, BlockProps.getInt(properties, "max_helices", 3));
        var ringSegments = Math.max(16, BlockProps.getInt(properties, "ring_segments", 88));
        var helixSegments = Math.max(32, BlockProps.getInt(properties, "helix_segments", 224));
        var helixTurns = BlockProps.getFloat(properties, "helix_turns", 8.5f);
        var width = BlockProps.getFloat(properties, "width", 0.12f);
        var rotationMin = BlockProps.getFloat(properties, "rotation_min", 0.82f);
        var rotationMax = BlockProps.getFloat(properties, "rotation_max", 5.2f);
        var irregularity = Math.max(0f, BlockProps.getFloat(properties, "irregularity", 0.095f));
        var centerWander = Math.max(0f, BlockProps.getFloat(properties, "center_wander", 0.055f));
        var fragmentation = BlockSim.clamp01(BlockProps.getFloat(properties, "fragmentation", 0.22f));
        var tilt = Math.max(0f, BlockProps.getFloat(properties, "tilt", 0.075f));
        var nestedRadius = Math.clamp(BlockProps.getFloat(properties, "nested_radius", 0.54f), 0.1f, 0.9f);
        var nestedWidth = Math.max(0f, BlockProps.getFloat(properties, "nested_width", 0.72f));
        var collapseStart = Math.clamp(
                BlockProps.getFloat(properties, "collapse_start", 0.8f), 0f, 0.99f);
        var collapseEnd = Math.clamp(
                BlockProps.getFloat(properties, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 0.075f);
        var dark = BlockProps.getColor(properties, "color_dark");
        var light = BlockProps.getColor(properties, "color_light");
        var edge = BlockProps.getColor(properties, "color_edge");
        long[] seed = {0L};
        return (buf, ctx) -> {
            var progress = BlockSim.clamp01(ctx.paramFloat(chargeParam, 0f));
            var expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            var expansion = BlockSim.clamp01((expandRate - expandMin) / (expandMax - expandMin));
            var collapse = BlockSim.smoothstep(BlockSim.clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            var collapseEase = collapse * collapse;
            var emission = BlockSim.clamp01(ctx.paramFloat(emissionParam, 1f));
            if (emission <= 0.001f || collapseEase >= 0.9995f) return;

            var radius = BlockSim.lerp(baseRadius, maxRadius, expansion);
            var height = BlockSim.lerp(baseHeight, maxHeight, expansion);
            var ringCount = Math.max(2, Math.round(BlockSim.lerp(minRings, maxRings, expansion)));
            var helixCount = Math.max(0, Math.round(BlockSim.lerp(minHelices, maxHelices, expansion)));
            var rotationSpeed = BlockSim.lerp(rotationMin, rotationMax, expansion) * (1f + collapse * 2.65f);
            var rotation = ctx.time() * rotationSpeed;
            var focusX = ctx.paramVec3(focusParam, 0, 0f);
            var focusY = ctx.paramVec3(focusParam, 1, 31f);
            var focusZ = ctx.paramVec3(focusParam, 2, 6f);
            var brightness = emission * BlockSim.lerp(0.54f, 1f, expansion);
            var liveWidth = width * BlockSim.lerp(0.72f, 1.18f, expansion) * (1f + collapse * 0.18f);

            for (var ring = 0; ring < ringCount; ring++) {
                var u = ringCount == 1 ? 0f : (float) ring / (ringCount - 1);
                var layerJitter = BlockSim.stableWave(ring, 1.71f) * height / Math.max(2f, ringCount) * 0.42f;
                var ringRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(u, 0.62));
                var ringY = Math.max(0f, Math.min(height, u * height + layerJitter));
                var ringPhase = rotation * (0.66f + u * 0.62f + BlockSim.stableWave(ring, 3.2f) * 0.06f)
                        + ring * 0.47f;
                var wander = ringRadius * centerWander * (0.35f + u * 0.65f);
                var centerX = (float) Math.sin(ring * 1.37f + ctx.time() * 0.19f) * wander;
                var centerZ = (float) Math.cos(ring * 1.91f - ctx.time() * 0.15f) * wander;
                var ellipse = 1f + BlockSim.stableWave(ring, 5.43f) * irregularity * 0.55f;
                var tiltX = BlockSim.stableWave(ring, 6.31f) * tilt;
                var tiltZ = BlockSim.stableWave(ring, 9.17f) * tilt;
                var ringWidthScale = 0.74f + BlockSim.stableUnit(ring, 4.29f) * 0.52f;
                var color = ring % 5 == 4 ? edge : ring % 2 == 0 ? light : dark;
                var fragmented = BlockSim.stableUnit(ring, 8.17f) < fragmentation;
                var fragmentCount = fragmented ? 2 + (ring % 4 == 3 ? 1 : 0) : 1;
                for (var fragment = 0; fragment < fragmentCount; fragment++) {
                    float startAngle;
                    float span;
                    if (fragmentCount == 1) {
                        startAngle = 0f;
                        span = (float) (Math.PI * 2.0);
                    } else {
                        startAngle = (float) (Math.PI * 2.0 * fragment / fragmentCount)
                                + BlockSim.stableWave(ring * 7 + fragment, 2.93f) * 0.38f;
                        var baseSpan = (float) (Math.PI * 2.0 / fragmentCount) * 0.78f;
                        span = baseSpan * (0.82f + BlockSim.stableUnit(ring * 11 + fragment, 4.61f) * 0.34f);
                    }
                    var points = Math.max(12, Math.round(ringSegments * span / (float) (Math.PI * 2.0)));
                    var arc = ArcSupport.add(ctx.arcs());
                    for (var point = 0; point <= points; point++) {
                        var t = (float) point / points;
                        var angle = startAngle + span * t + ringPhase;
                        var harmonic = (float) Math.sin(angle * 2f + ring * 1.13f) * 0.48f
                                + (float) Math.sin(angle * 5f - ring * 0.73f) * 0.31f
                                + (float) Math.sin(angle * 9f + ring * 2.07f) * 0.16f;
                        var ripple = 1f + irregularity * harmonic;
                        var x = centerX + (float) Math.cos(angle) * ringRadius * ripple * ellipse;
                        var z = centerZ + (float) Math.sin(angle) * ringRadius * ripple / ellipse;
                        var y = ringY
                                + (float) Math.cos(angle) * ringRadius * tiltX
                                + (float) Math.sin(angle) * ringRadius * tiltZ
                                + (float) Math.sin(angle + ring * 0.83f) * ringRadius * irregularity * 0.13f
                                + (float) Math.sin(angle * 3f - ringPhase) * 0.09f * (1f + u);
                        var widthRipple = 0.88f + 0.12f
                                * (float) Math.sin(angle * 2f + ring * 1.61f);
                        var pointWidth = liveWidth * ringWidthScale * widthRipple
                                * (0.88f + 0.12f * (float) Math.sin(t * Math.PI));
                        arc.addPoint(
                                BlockSim.lerp(x, focusX, collapseEase),
                                BlockSim.lerp(y, focusY, collapseEase),
                                BlockSim.lerp(z, focusZ, collapseEase),
                                pointWidth, 0f);
                    }
                    ArcSupport.configure(arc, color, brightness, lifetime, ++seed[0]);
                }

                if (nestedWidth > 0.001f) {
                    var innerRadius = ringRadius * nestedRadius
                            * (0.96f + BlockSim.stableWave(ring, 7.77f) * irregularity * 0.22f);
                    var innerArc = ArcSupport.add(ctx.arcs());
                    for (var point = 0; point <= ringSegments; point++) {
                        var t = (float) point / ringSegments;
                        var angle = t * (float) (Math.PI * 2.0) + ringPhase + 0.27f;
                        var ripple = 1f + irregularity * 0.62f
                                * ((float) Math.sin(angle * 3f - ring * 0.91f) * 0.7f
                                + (float) Math.sin(angle * 7f + ring * 1.37f) * 0.3f);
                        var x = centerX * 0.72f + (float) Math.cos(angle) * innerRadius * ripple * ellipse;
                        var z = centerZ * 0.72f + (float) Math.sin(angle) * innerRadius * ripple / ellipse;
                        var y = ringY
                                + (float) Math.cos(angle) * innerRadius * tiltX
                                + (float) Math.sin(angle) * innerRadius * tiltZ
                                + (float) Math.sin(angle * 2f + ring) * innerRadius * irregularity * 0.08f;
                        innerArc.addPoint(
                                BlockSim.lerp(x, focusX, collapseEase),
                                BlockSim.lerp(y, focusY, collapseEase),
                                BlockSim.lerp(z, focusZ, collapseEase),
                                liveWidth * ringWidthScale * nestedWidth
                                        * (0.9f + 0.1f * (float) Math.sin(angle * 3f)),
                                0f);
                    }
                    ArcSupport.configure(innerArc, dark, brightness * 0.62f, lifetime, ++seed[0]);
                }
            }

            for (var helix = 0; helix < helixCount; helix++) {
                var phase = rotation * (1.08f + BlockSim.stableWave(helix, 7.11f) * 0.09f)
                        + (float) (Math.PI * 2.0 * helix / helixCount);
                var arc = ArcSupport.add(ctx.arcs());
                for (var point = 0; point <= helixSegments; point++) {
                    var u = (float) point / helixSegments;
                    var localRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(u, 0.64));
                    var radialNoise = 1f + irregularity * (
                            (float) Math.sin(u * 17f + helix * 2.1f) * 0.52f
                                    + (float) Math.sin(u * 43f - helix * 1.3f) * 0.22f);
                    var angle = phase + u * helixTurns * (float) (Math.PI * 2.0)
                            + irregularity * 1.35f * (float) Math.sin(u * 11f + helix);
                    var helixWander = localRadius * centerWander * 0.58f;
                    var x = (float) Math.cos(angle) * localRadius * radialNoise
                            + (float) Math.sin(u * 8f + helix) * helixWander;
                    var y = u * height
                            + (float) Math.sin(u * 19f + helix * 1.7f) * height * irregularity * 0.012f;
                    var z = (float) Math.sin(angle) * localRadius * radialNoise
                            + (float) Math.cos(u * 7f - helix) * helixWander;
                    arc.addPoint(
                            BlockSim.lerp(x, focusX, collapseEase),
                            BlockSim.lerp(y, focusY, collapseEase),
                            BlockSim.lerp(z, focusZ, collapseEase),
                            liveWidth * 0.34f * (1.04f + 0.12f * (float) Math.sin(u * Math.PI)), 0f);
                }
                var color = helix % 3 == 2 ? edge : light;
                ArcSupport.configure(arc, color, brightness * 1.08f, lifetime, ++seed[0]);
            }
        };
    }
}
