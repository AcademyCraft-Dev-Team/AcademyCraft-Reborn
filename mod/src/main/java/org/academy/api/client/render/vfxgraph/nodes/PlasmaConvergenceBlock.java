package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class PlasmaConvergenceBlock {
    private PlasmaConvergenceBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        String progressParam = BlockProps.getString(properties, "progress_param", "focus_progress");
        String formationParam = BlockProps.getString(properties, "formation_param", "");
        int count = Math.max(8, BlockProps.getInt(properties, "count", 72));
        int armCount = Math.clamp(BlockProps.getInt(properties, "arm_count", 4), 2, 8);
        float turns = Math.max(0.5f, BlockProps.getFloat(properties, "turns", 5.5f));
        float stagger = Math.clamp(BlockProps.getFloat(properties, "stagger", 0.28f), 0f, 0.48f);
        float angularAcceleration = Math.max(0f, BlockProps.getFloat(properties, "angular_acceleration", 6.2f));
        float irregularity = Math.clamp(BlockProps.getFloat(properties, "irregularity", 0.09f), 0f, 0.35f);
        float startRadius = Math.max(0f, BlockProps.getFloat(properties, "start_radius", 18f));
        float startHeight = Math.max(0f, BlockProps.getFloat(properties, "start_height", 12f));
        float endRadius = Math.max(0f, BlockProps.getFloat(properties, "end_radius", 0.24f));
        float sizeMin = Math.max(0.01f, BlockProps.getFloat(properties, "size_min", 0.16f));
        float sizeMax = Math.max(sizeMin, BlockProps.getFloat(properties, "size_max", 0.88f));
        int surfaceBulges = Math.clamp(BlockProps.getInt(properties, "surface_bulges", 3), 2, 3);
        float surfaceRadius = Math.max(0.1f, BlockProps.getFloat(properties, "surface_radius", 6.65f));
        float surfacePulse = Math.max(0f, BlockProps.getFloat(properties, "surface_pulse", 0.42f));
        float lifetime = Math.max(1f, BlockProps.getFloat(properties, "lifetime", 120f));
        byte layer = ParticleBuffer.layerByte(BlockProps.getString(properties, "layer", "plasma_mote"));
        float[] color = BlockProps.getColor(properties, "color");
        boolean[] fired = {false};
        return (buf, ctx) -> {
            if (!fired[0]) {
                fired[0] = true;
                int start = buf.count();
                for (int index = 0; index < count; index++) {
                    int particle = buf.spawn();
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

            float progress = BlockSim.clamp01(ctx.paramFloat(progressParam, 0f));
            float formation = BlockSim.clamp01(ctx.paramFloat(formationParam, progress));
            float surfaceBlend = BlockSim.smoothstep(BlockSim.clamp01((progress - 0.60f) / 0.28f));
            int stepsPerArm = Math.max(1, (count + armCount - 1) / armCount);
            int moteIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != layer) continue;
                int localIndex = moteIndex++;
                float particleSeed = buf.seed(particle);
                int arm = localIndex % armCount;
                int step = localIndex / armCount;
                float pointOffset = 0.32f + BlockSim.stableUnit(localIndex, 9.37f) * 0.36f;
                float pathU = BlockSim.clamp01((step + pointOffset) / stepsPerArm);
                float localProgress = BlockSim.clamp01((progress * (1f + stagger * 2f) - pathU * stagger)
                        / (1f + stagger));
                float convergence = BlockSim.smoothstep(localProgress);
                float radialCollapse = (float) Math.pow(convergence, 1.42f);
                float absorption = BlockSim.smoothstep(BlockSim.clamp01((convergence - 0.80f) / 0.20f));

                float armPhase = arm * (float) (Math.PI * 2.0 / armCount);
                float pathPhase = pathU * turns * (float) (Math.PI * 2.0);
                float seedWander = BlockSim.stableWave(localIndex, 3.71f) * irregularity;
                float spinSpeed = 0.72f + angularAcceleration * convergence * convergence;
                float orbit = armPhase + pathPhase + seedWander
                        + ctx.time() * spinSpeed
                        + convergence * (float) Math.PI * (2.4f + pathU * 1.8f);
                float outerBand = 0.70f + pathU * 0.30f;
                float radiusNoise = 1f + BlockSim.stableWave(localIndex, 5.13f) * irregularity
                        * (1f - convergence * 0.74f);
                float radius = BlockSim.lerp(startRadius * outerBand * radiusNoise,
                        endRadius * (0.72f + BlockSim.stableUnit(localIndex, 7.19f) * 0.56f), radialCollapse);
                float verticalEnvelope = (pathU * 2f - 1f) * startHeight;
                float vertical = verticalEnvelope * (1f - (float) Math.pow(convergence, 0.82f));
                float x = (float) Math.cos(orbit) * radius;
                float z = (float) Math.sin(orbit) * radius;
                float y = vertical + (float) Math.sin(orbit * 1.45f + particleSeed)
                        * radius * irregularity * (1f - convergence * 0.68f);
                boolean surfaceBulge = localIndex < surfaceBulges;
                float bulgeWave = (float) Math.sin(ctx.time() * 0.72f + localIndex * 2.17f);
                if (surfaceBulge) {
                    float bulgeOrbit = ctx.time() * (0.14f + localIndex * 0.018f)
                            + localIndex * (float) (Math.PI * 2.0 / surfaceBulges)
                            + BlockSim.stableWave(localIndex, 8.43f) * 0.42f;
                    float latitude = surfaceBulges == 2
                            ? (localIndex == 0 ? -0.32f : 0.32f)
                            : (localIndex - 1) * 0.39f;
                    float liveSurfaceRadius = BlockSim.lerp(0.18f, surfaceRadius,
                            (float) Math.pow(formation, 2.3));
                    float protrusion = surfacePulse * bulgeWave * (0.28f + formation * 0.72f);
                    float bulgeRadius = Math.max(0.08f, liveSurfaceRadius + protrusion);
                    float latitudeRadius = (float) Math.cos(latitude) * bulgeRadius;
                    float bulgeX = (float) Math.cos(bulgeOrbit) * latitudeRadius;
                    float bulgeY = (float) Math.sin(latitude) * bulgeRadius;
                    float bulgeZ = (float) Math.sin(bulgeOrbit) * latitudeRadius;
                    x = BlockSim.lerp(x, bulgeX, surfaceBlend);
                    y = BlockSim.lerp(y, bulgeY, surfaceBlend);
                    z = BlockSim.lerp(z, bulgeZ, surfaceBlend);
                    orbit = bulgeOrbit;
                }
                buf.setPosition(particle, x, y, z);
                buf.setRotation(particle, orbit);
                float sizeVariation = 0.68f + BlockSim.stableUnit(localIndex, 8.91f) * 0.54f;
                float mergingSize = BlockSim.lerp(sizeMin, sizeMax, (float) Math.pow(convergence, 0.76f))
                        * sizeVariation * (1f - absorption * 0.78f);
                float bulgeSize = BlockSim.lerp(sizeMin * 0.92f, sizeMax * 0.82f, formation)
                        * (1f + bulgeWave * 0.075f);
                float liveSize = surfaceBulge
                        ? BlockSim.lerp(mergingSize, bulgeSize, surfaceBlend)
                        : mergingSize;
                buf.setSizeScaled(particle, liveSize);
                float mergingAlpha = color[3] * (0.58f + convergence * 0.42f) * (1f - absorption);
                float liveAlpha = surfaceBulge
                        ? BlockSim.lerp(mergingAlpha, color[3] * 0.9f, surfaceBlend)
                        : mergingAlpha;
                float heat = convergence * 0.30f;
                buf.setColorRgb(particle,
                        BlockSim.lerp(color[0], 1f, heat),
                        BlockSim.lerp(color[1], 0.82f, heat),
                        BlockSim.lerp(color[2], 1f, heat * 0.72f));
                buf.setAlpha(particle, liveAlpha);
            }
        };
    }
}
