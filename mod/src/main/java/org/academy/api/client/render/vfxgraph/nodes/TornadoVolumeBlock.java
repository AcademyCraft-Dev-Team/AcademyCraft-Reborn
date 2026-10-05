package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class TornadoVolumeBlock {
    private TornadoVolumeBlock() {
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
        float baseRadius = Math.max(bottomRadius, BlockProps.getFloat(properties, "base_radius", 15f));
        float maxRadius = Math.max(baseRadius, BlockProps.getFloat(properties, "max_radius", 96f));
        float baseHeight = Math.max(0.1f, BlockProps.getFloat(properties, "base_height", 32f));
        float maxHeight = Math.max(baseHeight, BlockProps.getFloat(properties, "max_height", 128f));
        int volumeCount = Math.max(24, BlockProps.getInt(properties, "volume_count", 480));
        int dustCount = Math.max(16, BlockProps.getInt(properties, "dust_count", 420));
        float volumeSize = Math.max(0.05f, BlockProps.getFloat(properties, "volume_size", 5.4f));
        float dustSize = Math.max(0.01f, BlockProps.getFloat(properties, "dust_size", 0.8f));
        float vortexStrength = BlockProps.getFloat(properties, "rotation_speed", 3.4f);
        float liftStrength = Math.max(0.001f, BlockProps.getFloat(properties, "rise_speed", 0.105f));
        float inwardForce = Math.max(0.05f, BlockProps.getFloat(properties, "inward_force", 3.2f));
        float turbulence = Math.max(0f, BlockProps.getFloat(properties, "turbulence", 0.18f));
        float drag = Math.max(0.05f, BlockProps.getFloat(properties, "drag", 2.4f));
        float volumeTurns = BlockProps.getFloat(properties, "volume_turns", 3.8f);
        float dustTurns = BlockProps.getFloat(properties, "dust_turns", 8.5f);
        float volumeRadiusScale = Math.max(1f, BlockProps.getFloat(properties, "volume_radius_scale", 1.45f));
        float dustRadiusScale = Math.max(1f, BlockProps.getFloat(properties, "dust_radius_scale", 1.55f));
        float collapseStart = Math.clamp(
                BlockProps.getFloat(properties, "collapse_start", 0.8f), 0f, 0.99f);
        float collapseEnd = Math.clamp(
                BlockProps.getFloat(properties, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        float lifetime = Math.max(1f, BlockProps.getFloat(properties, "lifetime", 120f));
        byte volumeLayer = ParticleBuffer.layerByte(BlockProps.getString(properties, "volume_layer", "wind_volume"));
        byte dustLayer = ParticleBuffer.layerByte(BlockProps.getString(properties, "dust_layer", "wind_dust"));
        float[] volumeColor = BlockProps.getColor(properties, "volume_color");
        float[] dustColor = BlockProps.getColor(properties, "dust_color");

        float[] volumeHeightState = new float[volumeCount];
        float[] volumeDepthState = new float[volumeCount];
        float[] volumeRadialVelocity = new float[volumeCount];
        float[] volumeLiftVelocity = new float[volumeCount];
        float[] volumeAngleState = new float[volumeCount];
        float[] volumeAngularVelocity = new float[volumeCount];
        float[] dustHeightState = new float[dustCount];
        float[] dustDepthState = new float[dustCount];
        float[] dustRadialVelocity = new float[dustCount];
        float[] dustLiftVelocity = new float[dustCount];
        float[] dustAngleState = new float[dustCount];
        float[] dustAngularVelocity = new float[dustCount];
        boolean[] fired = {false};
        return (buf, ctx) -> {
            float fullTurn = (float) (Math.PI * 2.0);
            if (!fired[0]) {
                fired[0] = true;
                int start = buf.count();
                for (int index = 0; index < volumeCount; index++) {
                    float u = ((index + 0.5f) / volumeCount + BlockSim.stableUnit(index, 7.47f) * 0.08f) % 1f;
                    volumeHeightState[index] = u;
                    volumeDepthState[index] = 0.68f + BlockSim.stableUnit(index, 4.17f) * 0.30f;
                    volumeRadialVelocity[index] = BlockSim.stableWave(index, 5.31f) * 0.025f;
                    volumeLiftVelocity[index] = liftStrength * (0.78f + BlockSim.stableUnit(index, 2.31f) * 0.44f);
                    volumeAngleState[index] = BlockSim.stableUnit(index, 1.19f) * fullTurn + u * volumeTurns * fullTurn;
                    volumeAngularVelocity[index] = vortexStrength * (0.88f + BlockSim.stableUnit(index, 8.03f) * 0.24f);
                    int particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, volumeSize);
                    buf.setColor(particle,
                            volumeColor[0], volumeColor[1], volumeColor[2], volumeColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, volumeLayer);
                }
                for (int index = 0; index < dustCount; index++) {
                    float u = ((index + 0.5f) / dustCount + BlockSim.stableUnit(index, 3.19f) * 0.05f) % 1f;
                    dustHeightState[index] = u;
                    dustDepthState[index] = 0.78f + BlockSim.stableUnit(index, 9.41f) * 0.21f;
                    dustRadialVelocity[index] = BlockSim.stableWave(index, 4.73f) * 0.04f;
                    dustLiftVelocity[index] = liftStrength * (1.72f + BlockSim.stableUnit(index, 6.23f) * 0.62f);
                    dustAngleState[index] = BlockSim.stableUnit(index, 2.67f) * fullTurn + u * dustTurns * fullTurn;
                    dustAngularVelocity[index] = vortexStrength * (1.18f + BlockSim.stableUnit(index, 5.29f) * 0.34f);
                    int particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, dustSize);
                    buf.setColor(particle, dustColor[0], dustColor[1], dustColor[2], dustColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, dustLayer);
                }
                ctx.emitBatch(start, buf.count());
            }

            float expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            float expansion = BlockSim.clamp01((expandRate - expandMin) / (expandMax - expandMin));
            float radius = BlockSim.lerp(baseRadius, maxRadius, expansion);
            float height = BlockSim.lerp(baseHeight, maxHeight, expansion);
            float progress = BlockSim.clamp01(ctx.paramFloat(chargeParam, 0f));
            float collapse = BlockSim.smoothstep(BlockSim.clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            float collapseEase = collapse * collapse;
            float emission = BlockSim.clamp01(ctx.paramFloat(emissionParam, 1f));
            float focusX = ctx.paramVec3(focusParam, 0, 0f);
            float focusY = ctx.paramVec3(focusParam, 1, 31f);
            float focusZ = ctx.paramVec3(focusParam, 2, 6f);
            float time = ctx.time();
            float dt = Math.clamp(ctx.dt(), 0f, 0.05f);
            float radialDamping = (float) Math.exp(-drag * dt);

            int volumeIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != volumeLayer) continue;
                int index = volumeIndex++;
                float particleSeed = buf.seed(particle);
                float u = volumeHeightState[index];
                float liftTarget = liftStrength * (0.78f + BlockSim.stableUnit(index, 2.31f) * 0.44f)
                        * (1f + expansion * 0.16f);
                float verticalNoise = (float) Math.sin(time * 0.83f + particleSeed * 1.37f)
                        * turbulence * 0.025f;
                volumeLiftVelocity[index] += ((liftTarget - volumeLiftVelocity[index]) * drag
                        + verticalNoise) * dt;
                u += volumeLiftVelocity[index] * dt;
                if (u >= 1f) {
                    u -= (float) Math.floor(u);
                    volumeDepthState[index] = 0.68f + BlockSim.stableUnit(index + (int) time, 4.17f) * 0.30f;
                    volumeRadialVelocity[index] = BlockSim.stableWave(index + (int) time, 5.31f) * 0.025f;
                }
                volumeHeightState[index] = u;

                float angularTarget = vortexStrength * (1.22f - u * 0.42f)
                        * (0.86f + BlockSim.stableUnit(index, 8.03f) * 0.28f);
                float angularNoise = (float) Math.sin(time * 0.91f + particleSeed * 0.73f)
                        * turbulence * 0.22f;
                volumeAngularVelocity[index] += ((angularTarget - volumeAngularVelocity[index]) * drag * 0.82f
                        + angularNoise) * dt;
                volumeAngleState[index] += volumeAngularVelocity[index] * dt;

                float targetDepth = 0.69f + BlockSim.stableUnit(index, 4.17f) * 0.27f;
                float centrifugal = volumeAngularVelocity[index] * volumeAngularVelocity[index] * 0.0065f;
                float radialNoise = (float) Math.sin(time * 1.07f + particleSeed * 1.91f + u * 11f)
                        * turbulence * 0.18f;
                float radialAcceleration = (targetDepth - volumeDepthState[index]) * inwardForce
                        + centrifugal * (1f - u * 0.38f) + radialNoise;
                volumeRadialVelocity[index] = (volumeRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                volumeDepthState[index] = Math.clamp(
                        volumeDepthState[index] + volumeRadialVelocity[index] * dt, 0.58f, 1.08f);

                float bezierHeight = u * u * (3f - 2f * u);
                float coneRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(bezierHeight, 0.72f))
                        * volumeRadiusScale;
                float radial = coneRadius * volumeDepthState[index];
                float angle = volumeAngleState[index];
                float wander = coneRadius * 0.045f;
                float x = (float) Math.cos(angle) * radial
                        + (float) Math.sin(time * 0.37f + u * 8f) * wander;
                float y = u * height
                        + (float) Math.sin(angle * 0.46f + particleSeed) * height * 0.012f;
                float z = (float) Math.sin(angle) * radial
                        + (float) Math.cos(time * 0.31f - u * 7f) * wander;
                buf.setPosition(particle,
                        BlockSim.lerp(x, focusX, collapseEase),
                        BlockSim.lerp(y, focusY, collapseEase),
                        BlockSim.lerp(z, focusZ, collapseEase));
                buf.setVelocity(particle,
                        -(float) Math.sin(angle) * volumeAngularVelocity[index] * radial,
                        volumeLiftVelocity[index] * height,
                        (float) Math.cos(angle) * volumeAngularVelocity[index] * radial);
                buf.setRotation(particle, angle + u * 0.7f);
                buf.setAge(particle, time);
                float endFade = BlockSim.smoothstep(BlockSim.clamp01(u / 0.11f))
                        * BlockSim.smoothstep(BlockSim.clamp01((1f - u) / 0.14f));
                float sizeVariation = 0.74f + BlockSim.stableUnit(index, 8.63f) * 0.52f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (0.82f + u * 0.34f) * (1f - collapseEase * 0.58f));
                buf.setAlpha(particle, volumeColor[3] * emission * endFade
                        * (0.62f + u * 0.38f) * (1f - collapseEase));
            }

            int dustIndex = 0;
            for (int particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != dustLayer) continue;
                int index = dustIndex++;
                float particleSeed = buf.seed(particle);
                float u = dustHeightState[index];
                float liftTarget = liftStrength * (1.72f + BlockSim.stableUnit(index, 6.23f) * 0.62f)
                        * (1f + expansion * 0.22f);
                float verticalNoise = (float) Math.sin(time * 1.31f - particleSeed * 0.91f)
                        * turbulence * 0.05f;
                dustLiftVelocity[index] += ((liftTarget - dustLiftVelocity[index]) * drag * 0.9f
                        + verticalNoise) * dt;
                u += dustLiftVelocity[index] * dt;
                if (u >= 1f) {
                    u -= (float) Math.floor(u);
                    dustDepthState[index] = 0.78f + BlockSim.stableUnit(index + (int) time, 9.41f) * 0.21f;
                    dustRadialVelocity[index] = BlockSim.stableWave(index + (int) time, 4.73f) * 0.04f;
                }
                dustHeightState[index] = u;

                float angularTarget = vortexStrength * (1.58f - u * 0.55f)
                        * (0.9f + BlockSim.stableUnit(index, 5.29f) * 0.3f);
                float angularNoise = (float) Math.sin(time * 1.47f + particleSeed * 1.17f)
                        * turbulence * 0.36f;
                dustAngularVelocity[index] += ((angularTarget - dustAngularVelocity[index]) * drag
                        + angularNoise) * dt;
                dustAngleState[index] += dustAngularVelocity[index] * dt;

                float targetDepth = 0.78f + BlockSim.stableUnit(index, 9.41f) * 0.19f;
                float centrifugal = dustAngularVelocity[index] * dustAngularVelocity[index] * 0.0045f;
                float radialNoise = (float) Math.sin(time * 1.79f + particleSeed * 0.63f + u * 17f)
                        * turbulence * 0.28f;
                float radialAcceleration = (targetDepth - dustDepthState[index]) * inwardForce * 1.18f
                        + centrifugal * (1f - u * 0.32f) + radialNoise;
                dustRadialVelocity[index] = (dustRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                dustDepthState[index] = Math.clamp(
                        dustDepthState[index] + dustRadialVelocity[index] * dt, 0.66f, 1.1f);

                float bezierHeight = u * u * (3f - 2f * u);
                float coneRadius = BlockSim.lerp(bottomRadius * 0.86f, radius * 0.96f,
                        (float) Math.pow(bezierHeight, 0.69f)) * dustRadiusScale;
                float radial = coneRadius * dustDepthState[index];
                float angle = dustAngleState[index];
                float x = (float) Math.cos(angle) * radial;
                float y = u * height
                        + (float) Math.sin(angle * 0.35f + particleSeed) * 0.38f;
                float z = (float) Math.sin(angle) * radial;
                buf.setPosition(particle,
                        BlockSim.lerp(x, focusX, collapseEase),
                        BlockSim.lerp(y, focusY, collapseEase),
                        BlockSim.lerp(z, focusZ, collapseEase));
                buf.setVelocity(particle,
                        -(float) Math.sin(angle) * dustAngularVelocity[index] * radial,
                        dustLiftVelocity[index] * height,
                        (float) Math.cos(angle) * dustAngularVelocity[index] * radial);
                buf.setRotation(particle, angle);
                buf.setAge(particle, time);
                float sizeVariation = 0.58f + BlockSim.stableUnit(index, 5.87f) * 0.82f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (1f - collapseEase * 0.72f));
                float endFade = BlockSim.smoothstep(BlockSim.clamp01(u / 0.075f))
                        * BlockSim.smoothstep(BlockSim.clamp01((1f - u) / 0.11f));
                buf.setAlpha(particle, dustColor[3] * emission * endFade
                        * (0.66f + u * 0.34f) * (1f - collapseEase));
            }
        };
    }
}
