package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class UpdateNodes {
    private UpdateNodes() {
    }

    static SimNode velocity(GraphNode node) {
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

    static SimNode gravity(GraphNode node) {
        var properties = node.properties();
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

    static SimNode force(GraphNode node) {
        var properties = node.properties();
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

    static SimNode noise(GraphNode node) {
        var properties = node.properties();
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

    static SimNode turbulence(GraphNode node) {
        var properties = node.properties();
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
                var ty = 0f;
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
                        buf.velocityY(i) + ty * n * amp * dt,
                        buf.velocityZ(i) + tz * n * amp * dt);
            }
        };
    }

    static SimNode vortex(GraphNode node) {
        var properties = node.properties();
        var cx = BlockProps.getFloat(properties, "cx", 0f);
        var cz = BlockProps.getFloat(properties, "cz", 0f);
        var strength = BlockProps.getFloat(properties, "strength", 1f);
        return (buf, ctx) -> {
            var dt = ctx.dt();
            for (var i = 0; i < buf.count(); i++) {
                var dx = buf.positionX(i) - cx;
                var dz = buf.positionZ(i) - cz;
                var r2 = dx * dx + dz * dz;
                if (r2 < 1e-6f) continue;
                var inv = 1f / (float) Math.sqrt(r2);
                var tx = -dz * inv * strength * dt;
                var tz = dx * inv * strength * dt;
                buf.setVelocity(i, buf.velocityX(i) + tx, buf.velocityY(i), buf.velocityZ(i) + tz);
            }
        };
    }

    static SimNode drag(GraphNode node) {
        var drag = BlockProps.getFloat(node.properties(), "drag", 0.1f);
        return (buf, ctx) -> {
            var factor = (float) Math.exp(-drag * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i,
                        buf.velocityX(i) * factor,
                        buf.velocityY(i) * factor,
                        buf.velocityZ(i) * factor);
            }
        };
    }

    static SimNode damping(GraphNode node) {
        var damping = BlockProps.getFloat(node.properties(), "damping", 0.5f);
        return (buf, ctx) -> {
            var factor = Math.max(0f, 1f - damping * ctx.dt());
            for (var i = 0; i < buf.count(); i++) {
                buf.setVelocity(i,
                        buf.velocityX(i) * factor,
                        buf.velocityY(i) * factor,
                        buf.velocityZ(i) * factor);
            }
        };
    }

    static SimNode age(GraphNode node) {
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

    static SimNode fade(GraphNode node) {
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var t = Math.min(1f, buf.age(i) / buf.lifetime(i));
                buf.setAlpha(i, buf.startAlpha(i) * (1f - t));
                buf.setSizeScaled(i, buf.startSize(i) * (1f - t));
            }
        };
    }
}
