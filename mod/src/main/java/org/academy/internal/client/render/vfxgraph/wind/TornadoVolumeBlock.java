package org.academy.internal.client.render.vfxgraph.wind;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.BlockProps;
import org.academy.api.client.render.vfxgraph.nodes.BlockSim;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class TornadoVolumeBlock {
    private TornadoVolumeBlock() {
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
        var baseRadius = Math.max(bottomRadius, BlockProps.getFloat(properties, "base_radius", 15f));
        var maxRadius = Math.max(baseRadius, BlockProps.getFloat(properties, "max_radius", 96f));
        var baseHeight = Math.max(0.1f, BlockProps.getFloat(properties, "base_height", 32f));
        var maxHeight = Math.max(baseHeight, BlockProps.getFloat(properties, "max_height", 128f));
        var volumeCount = Math.max(24, BlockProps.getInt(properties, "volume_count", 480));
        var dustCount = Math.max(16, BlockProps.getInt(properties, "dust_count", 420));
        var volumeSize = Math.max(0.05f, BlockProps.getFloat(properties, "volume_size", 5.4f));
        var dustSize = Math.max(0.01f, BlockProps.getFloat(properties, "dust_size", 0.8f));
        var vortexStrength = BlockProps.getFloat(properties, "rotation_speed", 3.4f);
        var liftStrength = Math.max(0.001f, BlockProps.getFloat(properties, "rise_speed", 0.105f));
        var inwardForce = Math.max(0.05f, BlockProps.getFloat(properties, "inward_force", 3.2f));
        var turbulence = Math.max(0f, BlockProps.getFloat(properties, "turbulence", 0.18f));
        var drag = Math.max(0.05f, BlockProps.getFloat(properties, "drag", 2.4f));
        var volumeTurns = BlockProps.getFloat(properties, "volume_turns", 3.8f);
        var dustTurns = BlockProps.getFloat(properties, "dust_turns", 8.5f);
        var volumeRadiusScale = Math.max(1f, BlockProps.getFloat(properties, "volume_radius_scale", 1.45f));
        var dustRadiusScale = Math.max(1f, BlockProps.getFloat(properties, "dust_radius_scale", 1.55f));
        var collapseStart = Math.clamp(
                BlockProps.getFloat(properties, "collapse_start", 0.8f), 0f, 0.99f);
        var collapseEnd = Math.clamp(
                BlockProps.getFloat(properties, "collapse_end", 0.94f), collapseStart + 0.01f, 1f);
        var lifetime = Math.max(1f, BlockProps.getFloat(properties, "lifetime", 120f));
        var volumeLayer = ParticleBuffer.layerByte(BlockProps.getString(properties, "volume_layer", "wind_volume"));
        var dustLayer = ParticleBuffer.layerByte(BlockProps.getString(properties, "dust_layer", "wind_dust"));
        var volumeColor = BlockProps.getColor(properties, "volume_color");
        var dustColor = BlockProps.getColor(properties, "dust_color");

        var volumeHeightState = new float[volumeCount];
        var volumeDepthState = new float[volumeCount];
        var volumeRadialVelocity = new float[volumeCount];
        var volumeLiftVelocity = new float[volumeCount];
        var volumeAngleState = new float[volumeCount];
        var volumeAngularVelocity = new float[volumeCount];
        var dustHeightState = new float[dustCount];
        var dustDepthState = new float[dustCount];
        var dustRadialVelocity = new float[dustCount];
        var dustLiftVelocity = new float[dustCount];
        var dustAngleState = new float[dustCount];
        var dustAngularVelocity = new float[dustCount];
        boolean[] fired = {false};
        return (buf, ctx) -> {
            var fullTurn = (float) (Math.PI * 2.0);
            if (!fired[0]) {
                fired[0] = true;
                var start = buf.count();
                for (var index = 0; index < volumeCount; index++) {
                    var u = ((index + 0.5f) / volumeCount + BlockSim.stableUnit(index, 7.47f) * 0.08f) % 1f;
                    volumeHeightState[index] = u;
                    volumeDepthState[index] = 0.68f + BlockSim.stableUnit(index, 4.17f) * 0.30f;
                    volumeRadialVelocity[index] = BlockSim.stableWave(index, 5.31f) * 0.025f;
                    volumeLiftVelocity[index] = liftStrength * (0.78f + BlockSim.stableUnit(index, 2.31f) * 0.44f);
                    volumeAngleState[index] = BlockSim.stableUnit(index, 1.19f) * fullTurn + u * volumeTurns * fullTurn;
                    volumeAngularVelocity[index] = vortexStrength * (0.88f + BlockSim.stableUnit(index, 8.03f) * 0.24f);
                    var particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, volumeSize);
                    buf.setColor(particle,
                            volumeColor[0], volumeColor[1], volumeColor[2], volumeColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, volumeLayer);
                }
                for (var index = 0; index < dustCount; index++) {
                    var u = ((index + 0.5f) / dustCount + BlockSim.stableUnit(index, 3.19f) * 0.05f) % 1f;
                    dustHeightState[index] = u;
                    dustDepthState[index] = 0.78f + BlockSim.stableUnit(index, 9.41f) * 0.21f;
                    dustRadialVelocity[index] = BlockSim.stableWave(index, 4.73f) * 0.04f;
                    dustLiftVelocity[index] = liftStrength * (1.72f + BlockSim.stableUnit(index, 6.23f) * 0.62f);
                    dustAngleState[index] = BlockSim.stableUnit(index, 2.67f) * fullTurn + u * dustTurns * fullTurn;
                    dustAngularVelocity[index] = vortexStrength * (1.18f + BlockSim.stableUnit(index, 5.29f) * 0.34f);
                    var particle = buf.spawn();
                    buf.setPosition(particle, 0f, 0f, 0f);
                    buf.setVelocity(particle, 0f, 0f, 0f);
                    buf.setSize(particle, dustSize);
                    buf.setColor(particle, dustColor[0], dustColor[1], dustColor[2], dustColor[3]);
                    buf.setLifetime(particle, lifetime);
                    buf.setLayer(particle, dustLayer);
                }
                ctx.emitBatch(start, buf.count());
            }

            var expandRate = Math.max(expandMin, Math.min(expandMax,
                    ctx.paramFloat(expandParam, expandMin)));
            var expansion = BlockSim.clamp01((expandRate - expandMin) / (expandMax - expandMin));
            var radius = BlockSim.lerp(baseRadius, maxRadius, expansion);
            var height = BlockSim.lerp(baseHeight, maxHeight, expansion);
            var progress = BlockSim.clamp01(ctx.paramFloat(chargeParam, 0f));
            var collapse = BlockSim.smoothstep(BlockSim.clamp01((progress - collapseStart) / (collapseEnd - collapseStart)));
            var collapseEase = collapse * collapse;
            var emission = BlockSim.clamp01(ctx.paramFloat(emissionParam, 1f));
            var focusX = ctx.paramVec3(focusParam, 0, 0f);
            var focusY = ctx.paramVec3(focusParam, 1, 31f);
            var focusZ = ctx.paramVec3(focusParam, 2, 6f);
            var time = ctx.time();
            var dt = Math.clamp(ctx.dt(), 0f, 0.05f);
            var radialDamping = (float) Math.exp(-drag * dt);

            var volumeIndex = 0;
            for (var particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != volumeLayer) continue;
                var index = volumeIndex++;
                var particleSeed = buf.seed(particle);
                var u = volumeHeightState[index];
                var liftTarget = liftStrength * (0.78f + BlockSim.stableUnit(index, 2.31f) * 0.44f)
                        * (1f + expansion * 0.16f);
                var verticalNoise = (float) Math.sin(time * 0.83f + particleSeed * 1.37f)
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

                var angularTarget = vortexStrength * (1.22f - u * 0.42f)
                        * (0.86f + BlockSim.stableUnit(index, 8.03f) * 0.28f);
                var angularNoise = (float) Math.sin(time * 0.91f + particleSeed * 0.73f)
                        * turbulence * 0.22f;
                volumeAngularVelocity[index] += ((angularTarget - volumeAngularVelocity[index]) * drag * 0.82f
                        + angularNoise) * dt;
                volumeAngleState[index] += volumeAngularVelocity[index] * dt;

                var targetDepth = 0.69f + BlockSim.stableUnit(index, 4.17f) * 0.27f;
                var centrifugal = volumeAngularVelocity[index] * volumeAngularVelocity[index] * 0.0065f;
                var radialNoise = (float) Math.sin(time * 1.07f + particleSeed * 1.91f + u * 11f)
                        * turbulence * 0.18f;
                var radialAcceleration = (targetDepth - volumeDepthState[index]) * inwardForce
                        + centrifugal * (1f - u * 0.38f) + radialNoise;
                volumeRadialVelocity[index] = (volumeRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                volumeDepthState[index] = Math.clamp(
                        volumeDepthState[index] + volumeRadialVelocity[index] * dt, 0.58f, 1.08f);

                var bezierHeight = u * u * (3f - 2f * u);
                var coneRadius = BlockSim.lerp(bottomRadius, radius, (float) Math.pow(bezierHeight, 0.72f))
                        * volumeRadiusScale;
                var radial = coneRadius * volumeDepthState[index];
                var angle = volumeAngleState[index];
                var wander = coneRadius * 0.045f;
                var x = (float) Math.cos(angle) * radial
                        + (float) Math.sin(time * 0.37f + u * 8f) * wander;
                var y = u * height
                        + (float) Math.sin(angle * 0.46f + particleSeed) * height * 0.012f;
                var z = (float) Math.sin(angle) * radial
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
                var endFade = BlockSim.smoothstep(BlockSim.clamp01(u / 0.11f))
                        * BlockSim.smoothstep(BlockSim.clamp01((1f - u) / 0.14f));
                var sizeVariation = 0.74f + BlockSim.stableUnit(index, 8.63f) * 0.52f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (0.82f + u * 0.34f) * (1f - collapseEase * 0.58f));
                buf.setAlpha(particle, volumeColor[3] * emission * endFade
                        * (0.62f + u * 0.38f) * (1f - collapseEase));
            }

            var dustIndex = 0;
            for (var particle = 0; particle < buf.count(); particle++) {
                if (buf.layer(particle) != dustLayer) continue;
                var index = dustIndex++;
                var particleSeed = buf.seed(particle);
                var u = dustHeightState[index];
                var liftTarget = liftStrength * (1.72f + BlockSim.stableUnit(index, 6.23f) * 0.62f)
                        * (1f + expansion * 0.22f);
                var verticalNoise = (float) Math.sin(time * 1.31f - particleSeed * 0.91f)
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

                var angularTarget = vortexStrength * (1.58f - u * 0.55f)
                        * (0.9f + BlockSim.stableUnit(index, 5.29f) * 0.3f);
                var angularNoise = (float) Math.sin(time * 1.47f + particleSeed * 1.17f)
                        * turbulence * 0.36f;
                dustAngularVelocity[index] += ((angularTarget - dustAngularVelocity[index]) * drag
                        + angularNoise) * dt;
                dustAngleState[index] += dustAngularVelocity[index] * dt;

                var targetDepth = 0.78f + BlockSim.stableUnit(index, 9.41f) * 0.19f;
                var centrifugal = dustAngularVelocity[index] * dustAngularVelocity[index] * 0.0045f;
                var radialNoise = (float) Math.sin(time * 1.79f + particleSeed * 0.63f + u * 17f)
                        * turbulence * 0.28f;
                var radialAcceleration = (targetDepth - dustDepthState[index]) * inwardForce * 1.18f
                        + centrifugal * (1f - u * 0.32f) + radialNoise;
                dustRadialVelocity[index] = (dustRadialVelocity[index] + radialAcceleration * dt)
                        * radialDamping;
                dustDepthState[index] = Math.clamp(
                        dustDepthState[index] + dustRadialVelocity[index] * dt, 0.66f, 1.1f);

                var bezierHeight = u * u * (3f - 2f * u);
                var coneRadius = BlockSim.lerp(bottomRadius * 0.86f, radius * 0.96f,
                        (float) Math.pow(bezierHeight, 0.69f)) * dustRadiusScale;
                var radial = coneRadius * dustDepthState[index];
                var angle = dustAngleState[index];
                var x = (float) Math.cos(angle) * radial;
                var y = u * height
                        + (float) Math.sin(angle * 0.35f + particleSeed) * 0.38f;
                var z = (float) Math.sin(angle) * radial;
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
                var sizeVariation = 0.58f + BlockSim.stableUnit(index, 5.87f) * 0.82f;
                buf.setSizeScaled(particle, buf.startSize(particle) * expandRate * sizeVariation
                        * (1f - collapseEase * 0.72f));
                var endFade = BlockSim.smoothstep(BlockSim.clamp01(u / 0.075f))
                        * BlockSim.smoothstep(BlockSim.clamp01((1f - u) / 0.11f));
                buf.setAlpha(particle, dustColor[3] * emission * endFade
                        * (0.66f + u * 0.34f) * (1f - collapseEase));
            }
        };
    }
}
