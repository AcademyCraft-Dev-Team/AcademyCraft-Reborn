package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class UpdateBlocks {
    private UpdateBlocks() {
    }

    static SimNode velocity(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                buf.setPosition(i,
                        buf.positionX(i) + buf.velocityX(i) * dt,
                        buf.positionY(i) + buf.velocityY(i) * dt,
                        buf.positionZ(i) + buf.velocityZ(i) * dt);
            }
        };
    }

    static SimNode gravity(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var g = BlockProps.getFloat(properties, "gravity", -9.8f);
        var param = BlockProps.getString(properties, "param", "");
        return (buf, ctx) -> {
            var rg = param.isEmpty() ? g : ctx.paramFloat(param, g);
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i), buf.velocityY(i) + rg * dt, buf.velocityZ(i));
            }
        };
    }

    static SimNode force(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var fx = BlockProps.getFloat(properties, "fx", 0f);
        var fy = BlockProps.getFloat(properties, "fy", 0f);
        var fz = BlockProps.getFloat(properties, "fz", 0f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                var m = buf.mass(i);
                var inv = m > 0f ? 1f / m : 1f;
                buf.setVelocity(i,
                        buf.velocityX(i) + fx * inv * dt,
                        buf.velocityY(i) + fy * inv * dt,
                        buf.velocityZ(i) + fz * inv * dt);
            }
        };
    }

    static SimNode noise(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var amp = BlockProps.getFloat(properties, "amplitude", 1f);
        var freq = BlockProps.getFloat(properties, "frequency", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var t = ctx.time() * freq;
            for (var i = 0; i < buf.count(); i++) {
                var nx = BlockSim.hash(buf.positionX(i) * freq + 1.7f, buf.positionY(i) * freq, t) * 2f - 1f;
                var ny = BlockSim.hash(buf.positionY(i) * freq, t + 3.1f, buf.positionZ(i) * freq) * 2f - 1f;
                var nz = BlockSim.hash(t + 5.3f, buf.positionZ(i) * freq, buf.positionX(i) * freq) * 2f - 1f;
                buf.setVelocity(i,
                        buf.velocityX(i) + nx * amp * dt,
                        buf.velocityY(i) + ny * amp * dt,
                        buf.velocityZ(i) + nz * amp * dt);
            }
        };
    }

    static SimNode turbulence(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var amp = BlockProps.getFloat(properties, "amplitude", 1f);
        var freq = BlockProps.getFloat(properties, "frequency", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var t = ctx.time() * freq;
            for (var i = 0; i < buf.count(); i++) {
                var n = BlockSim.hash(buf.positionX(i) * freq, buf.positionY(i) * freq, t) * 2f - 1f;
                var vx = buf.velocityX(i);
                var vy = buf.velocityY(i);
                var vz = buf.velocityZ(i);
                var len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len < 1e-5f) continue;
                vx /= len;
                vy /= len;
                vz /= len;
                var tx = vz;
                var tz = -vx;
                var tl = (float) Math.sqrt(tx * tx + tz * tz);
                if (tl < 1e-5f) {
                    tx = 1f;
                    tz = 0f;
                } else {
                    tx /= tl;
                    tz /= tl;
                }
                buf.setVelocity(i,
                        buf.velocityX(i) + tx * n * amp * dt,
                        buf.velocityY(i) + vy * n * amp * dt,
                        buf.velocityZ(i) + tz * n * amp * dt);
            }
        };
    }

    static SimNode vortex(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        float cx = BlockProps.getFloat(properties, "cx", 0f);
        float cy = BlockProps.getFloat(properties, "cy", 0f);
        float cz = BlockProps.getFloat(properties, "cz", 0f);
        float strength = BlockProps.getFloat(properties, "strength", 1f);
        float pull = BlockProps.getFloat(properties, "pull", 0f);
        float verticalPull = BlockProps.getFloat(properties, "vertical_pull", 0f);
        byte filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            float dt = ctx.dt();
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                float dx = buf.positionX(i) - cx;
                float dz = buf.positionZ(i) - cz;
                float r2 = dx * dx + dz * dz;
                if (r2 < 1e-6f) continue;
                float inv = 1f / (float) Math.sqrt(r2);
                float radialX = dx * inv;
                float radialZ = dz * inv;
                float vx = buf.velocityX(i);
                float vz = buf.velocityZ(i);
                float radialSpeed = vx * radialX + vz * radialZ;
                float targetRadialSpeed = -pull;
                if (radialSpeed > targetRadialSpeed) {
                    float correction = radialSpeed - targetRadialSpeed;
                    vx -= radialX * correction;
                    vz -= radialZ * correction;
                }
                buf.setVelocity(i,
                        vx - radialZ * strength * dt,
                        buf.velocityY(i) + (cy - buf.positionY(i)) * verticalPull * dt,
                        vz + radialX * strength * dt);
            }
        };
    }

    static SimNode follow(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        String positionParam = BlockProps.getString(properties, "position_param", "projectile_position");
        String directionParam = BlockProps.getString(properties, "direction_param", "projectile_direction");
        byte filter = BlockSim.layerFilter(properties);
        float orbitRadius = BlockProps.getFloat(properties, "orbit_radius", 0f);
        float orbitSpeed = BlockProps.getFloat(properties, "orbit_speed", 0f);
        float phase = BlockProps.getFloat(properties, "phase", 0f);
        float backOffset = BlockProps.getFloat(properties, "back_offset", 0f);
        String sizeParam = BlockProps.getString(properties, "size_param", "");
        float sizeMin = BlockProps.getFloat(properties, "size_min", 1f);
        float sizeMax = BlockProps.getFloat(properties, "size_max", sizeMin);
        float sizePower = Math.max(0.05f, BlockProps.getFloat(properties, "size_power", 1f));
        return (buf, ctx) -> {
            float px = ctx.paramVec3(positionParam, 0, 0f);
            float py = ctx.paramVec3(positionParam, 1, 0f);
            float pz = ctx.paramVec3(positionParam, 2, 0f);
            float fx = ctx.paramVec3(directionParam, 0, 0f);
            float fy = ctx.paramVec3(directionParam, 1, 0f);
            float fz = ctx.paramVec3(directionParam, 2, 1f);
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (fl < 1e-5f) {
                fx = 0f;
                fy = 0f;
                fz = 1f;
            } else {
                fx /= fl;
                fy /= fl;
                fz /= fl;
            }

            float rx = -fz;
            float ry = 0f;
            float rz = fx;
            float rl = (float) Math.sqrt(rx * rx + rz * rz);
            if (rl < 1e-4f) {
                rx = 1f;
                rz = 0f;
            } else {
                rx /= rl;
                rz /= rl;
            }
            float ux = ry * fz - rz * fy;
            float uy = rz * fx - rx * fz;
            float uz = rx * fy - ry * fx;
            float sizeT = (float) Math.pow(BlockSim.clamp01(ctx.paramFloat(sizeParam, 0f)), sizePower);
            float liveSize = BlockSim.lerp(sizeMin, sizeMax, BlockSim.smoothstep(sizeT));
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                float angle = ctx.time() * orbitSpeed + phase + buf.seed(i) * 2.3999632f;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                buf.setPosition(i,
                        px - fx * backOffset + orbitRadius * (rx * cos + ux * sin),
                        py - fy * backOffset + orbitRadius * (ry * cos + uy * sin),
                        pz - fz * backOffset + orbitRadius * (rz * cos + uz * sin));
                if (!sizeParam.isEmpty()) {
                    buf.setSize(i, liveSize);
                }
            }
        };
    }

    static SimNode live(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        byte filter = BlockSim.layerFilter(properties);
        String sizeParam = BlockProps.getString(properties, "size_param", "");
        float sizeScale = BlockProps.getFloat(properties, "size_scale", 1f);
        String alphaParam = BlockProps.getString(properties, "alpha_param", "");
        float alphaScale = BlockProps.getFloat(properties, "alpha_scale", 1f);
        String rotationParam = BlockProps.getString(properties, "rotation_param", "");
        float rotationScale = BlockProps.getFloat(properties, "rotation_scale", 1f);
        return (buf, ctx) -> {
            float liveSize = Math.max(0f, ctx.paramFloat(sizeParam, 1f) * sizeScale);
            float liveAlpha = BlockSim.clamp01(ctx.paramFloat(alphaParam, 1f) * alphaScale);
            float liveRotation = ctx.paramFloat(rotationParam, 0f) * rotationScale;
            for (int i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                if (!sizeParam.isEmpty()) {
                    buf.setSize(i, liveSize);
                }
                if (!alphaParam.isEmpty()) {
                    buf.setAlpha(i, liveAlpha);
                }
                if (!rotationParam.isEmpty()) {
                    buf.setRotation(i, liveRotation);
                }
            }
        };
    }

    static SimNode drag(VfxBlock block, PortValueSource ports) {
        var drag = BlockProps.getFloat(block.properties(), "drag", 0.1f);
        return (buf, ctx) -> {
            var factor = (float) Math.exp(-drag * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i) * factor, buf.velocityY(i) * factor, buf.velocityZ(i) * factor);
            }
        };
    }

    static SimNode damping(VfxBlock block, PortValueSource ports) {
        var damping = BlockProps.getFloat(block.properties(), "damping", 0.5f);
        return (buf, ctx) -> {
            var factor = Math.max(0f, 1f - damping * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i, buf.velocityX(i) * factor, buf.velocityY(i) * factor, buf.velocityZ(i) * factor);
            }
        };
    }

    static SimNode age(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            var dt = ctx.dt();
            var i = 0;
            while (i < buf.count()) {
                var age = buf.age(i) + dt;
                buf.setAge(i, age);
                if (age >= buf.lifetime(i)) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }

    static SimNode fade(VfxBlock block, PortValueSource ports) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var t = Math.min(1f, buf.age(i) / buf.lifetime(i));
                buf.setAlpha(i, buf.startAlpha(i) * (1f - t));
                buf.setSizeScaled(i, buf.startSize(i) * (1f - t));
            }
        };
    }
}
