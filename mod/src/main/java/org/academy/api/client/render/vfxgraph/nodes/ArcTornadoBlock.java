package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class ArcTornadoBlock {
    private ArcTornadoBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        String expandParam = BlockProps.getString(properties, "expand_param", "expand_rate");
        String chargeParam = BlockProps.getString(properties, "charge_param", "charge_progress");
        String emissionParam = BlockProps.getString(properties, "emission_param", "emission");
        String focusParam = BlockProps.getString(properties, "focus_param", "focus_offset");
        float expandMin = BlockProps.getFloat(properties, "expand_min", 0.5f);
        float expandMax = Math.max(expandMin + 0.001f, BlockProps.getFloat(properties, "expand_max", 3f));
        float bottomRadius = Math.max(0.1f, BlockProps.getFloat(properties, "bottom_radius", 15f));
        float baseRadius = BlockProps.getFloat(properties, "base_radius", 15f);
        float maxRadius = BlockProps.getFloat(properties, "max_radius", 96f);
        float baseHeight = BlockProps.getFloat(properties, "base_height", 32f);
        float maxHeight = BlockProps.getFloat(properties, "max_height", 128f);
        int minRings = BlockProps.getInt(properties, "min_rings", 9);
        int maxRings = BlockProps.getInt(properties, "max_rings", 30);
        int minHelices = Math.max(0, BlockProps.getInt(properties, "min_helices", 1));
        int maxHelices = Math.max(minHelices, BlockProps.getInt(properties, "max_helices", 3));
        int ringSegments = Math.max(16, BlockProps.getInt(properties, "ring_segments", 88));
        int helixSegments = Math.max(32, BlockProps.getInt(properties, "helix_segments", 224));
        float helixTurns = BlockProps.getFloat(properties, "helix_turns", 8.5f);
        float width = BlockProps.getFloat(properties, "width", 0.12f);
        float rotationMin = BlockProps.getFloat(properties, "rotation_min", 0.82f);
        float rotationMax = BlockProps.getFloat(properties, "rotation_max", 5.2f);
        float irregularity = Math.max(0f, BlockProps.getFloat(properties, "irregularity", 0.095f));
        float centerWander = Math.max(0f, BlockProps.getFloat(properties, "center_wander", 0.055f));
        float fragmentation = BlockSim.clamp01(BlockProps.getFloat(properties, "fragmentation", 0.22f));
        float tilt = Math.max(0f, BlockProps.getFloat(properties, "tilt", 0.075f));
        float nestedRadius = Math.clamp(BlockProps.getFloat(properties, "nested_radius", 0.54f), 0.1f, 0.9f);
        float nestedWidth = Math.max(0f, BlockProps.getFloat(properties, "nested_width", 0.72f));
        float collapseStart = Math.clamp(
                BlockProps.getFloat(properties, "collapse_start", 0.8f), 0f, 0.99f);
        float collapseEnd = Math.clamp(
                BlockProps.getFloat(properties, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        float lifetime = BlockProps.getFloat(properties, "lifetime", 0.075f);
        float[] dark = BlockProps.getColor(properties, "color_dark");
        float[] light = BlockProps.getColor(properties, "color_light");
        float[] edge = BlockProps.getColor(properties, "color_edge");
        long[] seed = {0L};
        return (buf, ctx) -> {
            float progress = BlockSim.clamp01(ctx.paramFloat(chargeParam, 0f));
            float expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            float expansion = BlockSim.clamp01((expandRate - expandMin) / (expandMax - expandMin));
            float collapse = BlockSim.smoothstep(BlockSim.clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            float collapseEase = collapse * collapse;
            float emission = BlockSim.clamp01(ctx.paramFloat(emissionParam, 1f));
            if (emission <= 0.001f || collapseEase >= 0.9995f) return;

            float radius = BlockSim.lerp(baseRadius, maxRadius, expansion);
            float height = BlockSim.lerp(baseHeight, maxHeight, expansion);
            int ringCount = Math.max(2, Math.round(BlockSim.lerp(minRings, maxRings, expansion)));
            int helixCount = Math.max(0, Math.round(BlockSim.lerp(minHelices, maxHelices, expansion)));
            float rotationSpeed = BlockSim.lerp(rotationMin, rotationMax, expansion) * (1f + collapse * 2.65f);
            float rotation = ctx.time() * rotationSpeed;
            float focusX = ctx.paramVec3(focusParam, 0, 0f);
            float focusY = ctx.paramVec3(focusParam, 1, 31f);
            float focusZ = ctx.paramVec3(focusParam, 2, 6f);
            float brightness = emission * BlockSim.lerp(0.54f, 1f, expansion);
            float liveWidth = width * BlockSim.lerp(0.72f, 1.18f, expansion) * (1f + collapse * 0.18f);

            for (int ring = 0; ring < ringCount; ring++) {
                float u = ringCount == 1 ? 0f : (float) ring / (ringCount - 1);
                float layerJitter = BlockSim.stableWave(ring, 1.71f) * height / Math.max(2f, ringCount) * 0.42f;
                float ringRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(u, 0.62));
                float ringY = Math.max(0f, Math.min(height, u * height + layerJitter));
                float ringPhase = rotation * (0.66f + u * 0.62f + BlockSim.stableWave(ring, 3.2f) * 0.06f)
                        + ring * 0.47f;
                float wander = ringRadius * centerWander * (0.35f + u * 0.65f);
                float centerX = (float) Math.sin(ring * 1.37f + ctx.time() * 0.19f) * wander;
                float centerZ = (float) Math.cos(ring * 1.91f - ctx.time() * 0.15f) * wander;
                float ellipse = 1f + BlockSim.stableWave(ring, 5.43f) * irregularity * 0.55f;
                float tiltX = BlockSim.stableWave(ring, 6.31f) * tilt;
                float tiltZ = BlockSim.stableWave(ring, 9.17f) * tilt;
                float ringWidthScale = 0.74f + BlockSim.stableUnit(ring, 4.29f) * 0.52f;
                float[] color = ring % 5 == 4 ? edge : ring % 2 == 0 ? light : dark;
                boolean fragmented = BlockSim.stableUnit(ring, 8.17f) < fragmentation;
                int fragmentCount = fragmented ? 2 + (ring % 4 == 3 ? 1 : 0) : 1;
                for (int fragment = 0; fragment < fragmentCount; fragment++) {
                    float startAngle;
                    float span;
                    if (fragmentCount == 1) {
                        startAngle = 0f;
                        span = (float) (Math.PI * 2.0);
                    } else {
                        startAngle = (float) (Math.PI * 2.0 * fragment / fragmentCount)
                                + BlockSim.stableWave(ring * 7 + fragment, 2.93f) * 0.38f;
                        float baseSpan = (float) (Math.PI * 2.0 / fragmentCount) * 0.78f;
                        span = baseSpan * (0.82f + BlockSim.stableUnit(ring * 11 + fragment, 4.61f) * 0.34f);
                    }
                    int points = Math.max(12, Math.round(ringSegments * span / (float) (Math.PI * 2.0)));
                    var arc = ctx.arcs().add();
                    for (int point = 0; point <= points; point++) {
                        float t = (float) point / points;
                        float angle = startAngle + span * t + ringPhase;
                        float harmonic = (float) Math.sin(angle * 2f + ring * 1.13f) * 0.48f
                                + (float) Math.sin(angle * 5f - ring * 0.73f) * 0.31f
                                + (float) Math.sin(angle * 9f + ring * 2.07f) * 0.16f;
                        float ripple = 1f + irregularity * harmonic;
                        float x = centerX + (float) Math.cos(angle) * ringRadius * ripple * ellipse;
                        float z = centerZ + (float) Math.sin(angle) * ringRadius * ripple / ellipse;
                        float y = ringY
                                + (float) Math.cos(angle) * ringRadius * tiltX
                                + (float) Math.sin(angle) * ringRadius * tiltZ
                                + (float) Math.sin(angle + ring * 0.83f) * ringRadius * irregularity * 0.13f
                                + (float) Math.sin(angle * 3f - ringPhase) * 0.09f * (1f + u);
                        float widthRipple = 0.88f + 0.12f
                                * (float) Math.sin(angle * 2f + ring * 1.61f);
                        float pointWidth = liveWidth * ringWidthScale * widthRipple
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
                    float innerRadius = ringRadius * nestedRadius
                            * (0.96f + BlockSim.stableWave(ring, 7.77f) * irregularity * 0.22f);
                    var innerArc = ctx.arcs().add();
                    for (int point = 0; point <= ringSegments; point++) {
                        float t = (float) point / ringSegments;
                        float angle = t * (float) (Math.PI * 2.0) + ringPhase + 0.27f;
                        float ripple = 1f + irregularity * 0.62f
                                * ((float) Math.sin(angle * 3f - ring * 0.91f) * 0.7f
                                + (float) Math.sin(angle * 7f + ring * 1.37f) * 0.3f);
                        float x = centerX * 0.72f + (float) Math.cos(angle) * innerRadius * ripple * ellipse;
                        float z = centerZ * 0.72f + (float) Math.sin(angle) * innerRadius * ripple / ellipse;
                        float y = ringY
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

            for (int helix = 0; helix < helixCount; helix++) {
                float phase = rotation * (1.08f + BlockSim.stableWave(helix, 7.11f) * 0.09f)
                        + (float) (Math.PI * 2.0 * helix / helixCount);
                var arc = ctx.arcs().add();
                for (int point = 0; point <= helixSegments; point++) {
                    float u = (float) point / helixSegments;
                    float localRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(u, 0.64));
                    float radialNoise = 1f + irregularity * (
                            (float) Math.sin(u * 17f + helix * 2.1f) * 0.52f
                                    + (float) Math.sin(u * 43f - helix * 1.3f) * 0.22f);
                    float angle = phase + u * helixTurns * (float) (Math.PI * 2.0)
                            + irregularity * 1.35f * (float) Math.sin(u * 11f + helix);
                    float helixWander = localRadius * centerWander * 0.58f;
                    float x = (float) Math.cos(angle) * localRadius * radialNoise
                            + (float) Math.sin(u * 8f + helix) * helixWander;
                    float y = u * height
                            + (float) Math.sin(u * 19f + helix * 1.7f) * height * irregularity * 0.012f;
                    float z = (float) Math.sin(angle) * localRadius * radialNoise
                            + (float) Math.cos(u * 7f - helix) * helixWander;
                    arc.addPoint(
                            BlockSim.lerp(x, focusX, collapseEase),
                            BlockSim.lerp(y, focusY, collapseEase),
                            BlockSim.lerp(z, focusZ, collapseEase),
                            liveWidth * 0.34f * (1.04f + 0.12f * (float) Math.sin(u * Math.PI)), 0f);
                }
                float[] color = helix % 3 == 2 ? edge : light;
                ArcSupport.configure(arc, color, brightness * 1.08f, lifetime, ++seed[0]);
            }
        };
    }
}
