package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.BlockProps;
import org.academy.api.client.render.vfxgraph.nodes.BlockSim;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class PlasmaConvergenceBlock {
    private PlasmaConvergenceBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var progressParam = BlockProps.getString(properties, "progress_param", "focus_progress");
        var formationParam = BlockProps.getString(properties, "formation_param", "");
        var count = Math.max(8, BlockProps.getInt(properties, "count", 72));
        var armCount = Math.clamp(BlockProps.getInt(properties, "arm_count", 4), 2, 8);
        var turns = Math.max(0.5f, BlockProps.getFloat(properties, "turns", 5.5f));
        var stagger = Math.clamp(BlockProps.getFloat(properties, "stagger", 0.28f), 0f, 0.48f);
        var angularAcceleration = Math.max(0f, BlockProps.getFloat(properties, "angular_acceleration", 6.2f));
        var irregularity = Math.clamp(BlockProps.getFloat(properties, "irregularity", 0.09f), 0f, 0.35f);
        var startRadius = Math.max(0f, BlockProps.getFloat(properties, "start_radius", 18f));
        var startHeight = Math.max(0f, BlockProps.getFloat(properties, "start_height", 12f));
        var endRadius = Math.max(0f, BlockProps.getFloat(properties, "end_radius", 0.24f));
        var sizeMin = Math.max(0.01f, BlockProps.getFloat(properties, "size_min", 0.16f));
        var sizeMax = Math.max(sizeMin, BlockProps.getFloat(properties, "size_max", 0.88f));
        var surfaceBulges = Math.clamp(BlockProps.getInt(properties, "surface_bulges", 3), 2, 3);
        var surfaceRadius = Math.max(0.1f, BlockProps.getFloat(properties, "surface_radius", 6.65f));
        var surfacePulse = Math.max(0f, BlockProps.getFloat(properties, "surface_pulse", 0.42f));
        var lifetime = Math.max(1f, BlockProps.getFloat(properties, "lifetime", 120f));
        var layer = ParticleBuffer.layerByte(BlockProps.getString(properties, "layer", "plasma_mote"));
        var color = BlockProps.getColor(properties, "color");
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (!fired[0]) {
                fired[0] = true;
                var start = buf.count();
                for (var index = 0; index < count; index++) {
                    var particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, sizeMin);
                    buf.setColor(particle, color[0], color[1], color[2], color[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setAge(particle, 0f);
                    buf.setLayer(particle, layer);
                }
                ctx.emitBatch(start, buf.count());
            }

            var progress = BlockSim.clamp01(ctx.paramFloat(progressParam, 0f));
            var formation = BlockSim.clamp01(ctx.paramFloat(formationParam, progress));
            var surfaceBlend = BlockSim.smoothstep(BlockSim.clamp01((progress - 0.60f) / 0.28f));
            var stepsPerArm = Math.max(1, (count + armCount - 1) / armCount);
            var moteIndex = 0;
            for (var particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != layer) continue;
                var localIndex = moteIndex++;
                var particleSeed = buf.seed(particle);
                var arm = localIndex % armCount;
                var step = localIndex / armCount;
                var pointOffset = 0.32f + BlockSim.stableUnit(localIndex, 9.37f) * 0.36f;
                var pathU = BlockSim.clamp01((step + pointOffset) / stepsPerArm);
                var localProgress = BlockSim.clamp01((progress * (1f + stagger * 2f) - pathU * stagger)
                        / (1f + stagger));
                var convergence = BlockSim.smoothstep(localProgress);
                var radialCollapse = (float) Math.pow(convergence, 1.42f);
                var absorption = BlockSim.smoothstep(BlockSim.clamp01((convergence - 0.80f) / 0.20f));

                var armPhase = arm * (float) (Math.PI * 2.0 / armCount);
                var pathPhase = pathU * turns * (float) (Math.PI * 2.0);
                var seedWander = BlockSim.stableWave(localIndex, 3.71f) * irregularity;
                var spinSpeed = 0.72f + angularAcceleration * convergence * convergence;
                var orbit = armPhase + pathPhase + seedWander
                        + ctx.time() * spinSpeed
                        + convergence * (float) Math.PI * (2.4f + pathU * 1.8f);
                var outerBand = 0.70f + pathU * 0.30f;
                var radiusNoise = 1f + BlockSim.stableWave(localIndex, 5.13f) * irregularity
                        * (1f - convergence * 0.74f);
                var radius = BlockSim.lerp(startRadius * outerBand * radiusNoise,
                        endRadius * (0.72f + BlockSim.stableUnit(localIndex, 7.19f) * 0.56f), radialCollapse);
                var verticalEnvelope = (pathU * 2f - 1f) * startHeight;
                var vertical = verticalEnvelope * (1f - (float) Math.pow(convergence, 0.82f));
                var x = (float) Math.cos(orbit) * radius;
                var z = (float) Math.sin(orbit) * radius;
                var y = vertical + (float) Math.sin(orbit * 1.45f + particleSeed)
                        * radius * irregularity * (1f - convergence * 0.68f);
                var surfaceBulge = localIndex < surfaceBulges;
                var bulgeWave = (float) Math.sin(ctx.time() * 0.72f + localIndex * 2.17f);
                if (surfaceBulge) {
                    var bulgeOrbit = ctx.time() * (0.14f + localIndex * 0.018f)
                            + localIndex * (float) (Math.PI * 2.0 / surfaceBulges)
                            + BlockSim.stableWave(localIndex, 8.43f) * 0.42f;
                    var latitude = surfaceBulges == 2
                            ? (localIndex == 0 ? -0.32f : 0.32f)
                            : (localIndex - 1) * 0.39f;
                    var liveSurfaceRadius = BlockSim.lerp(0.18f, surfaceRadius,
                            (float) Math.pow(formation, 2.3));
                    var protrusion = surfacePulse * bulgeWave * (0.28f + formation * 0.72f);
                    var bulgeRadius = Math.max(0.08f, liveSurfaceRadius + protrusion);
                    var latitudeRadius = (float) Math.cos(latitude) * bulgeRadius;
                    var bulgeX = (float) Math.cos(bulgeOrbit) * latitudeRadius;
                    var bulgeY = (float) Math.sin(latitude) * bulgeRadius;
                    var bulgeZ = (float) Math.sin(bulgeOrbit) * latitudeRadius;
                    x = BlockSim.lerp(x, bulgeX, surfaceBlend);
                    y = BlockSim.lerp(y, bulgeY, surfaceBlend);
                    z = BlockSim.lerp(z, bulgeZ, surfaceBlend);
                    orbit = bulgeOrbit;
                }
                buf.setPosition(particle, x, y, z);
                buf.setRotation(particle, orbit);
                var sizeVariation = 0.68f + BlockSim.stableUnit(localIndex, 8.91f) * 0.54f;
                var mergingSize = BlockSim.lerp(sizeMin, sizeMax, (float) Math.pow(convergence, 0.76f))
                        * sizeVariation * (1f - absorption * 0.78f);
                var bulgeSize = BlockSim.lerp(sizeMin * 0.92f, sizeMax * 0.82f, formation)
                        * (1f + bulgeWave * 0.075f);
                var liveSize = surfaceBulge
                        ? BlockSim.lerp(mergingSize, bulgeSize, surfaceBlend)
                        : mergingSize;
                buf.setSizeScaled(particle, liveSize);
                var mergingAlpha = color[3] * (0.58f + convergence * 0.42f) * (1f - absorption);
                var liveAlpha = surfaceBulge
                        ? BlockSim.lerp(mergingAlpha, color[3] * 0.9f, surfaceBlend)
                        : mergingAlpha;
                var heat = convergence * 0.30f;
                buf.setColorRgb(particle,
                        BlockSim.lerp(color[0], 1f, heat),
                        BlockSim.lerp(color[1], 0.82f, heat),
                        BlockSim.lerp(color[2], 1f, heat * 0.72f));
                buf.setAlpha(particle, liveAlpha);
            }
        };
    }
}
