package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.Map;

final class CollisionBlocks {
    private CollisionBlocks() {
    }

    static SimNode ground(VfxBlock block, PortValueSource ports) {
        return planeAt(block.properties());
    }

    static SimNode plane(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        return planeAt(BlockProps.getFloat(properties, "height", 0f), properties);
    }

    private static SimNode planeAt(Map<String, String> properties) {
        return planeAt(0f, properties);
    }

    private static SimNode planeAt(float height, Map<String, String> properties) {
        var bounce = BlockProps.getFloat(properties, "bounce", 0.5f);
        var kill = BlockProps.getBool(properties, "kill", false);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                if (buf.positionY(i) <= height && buf.velocityY(i) < 0f) {
                    if (kill) {
                        buf.kill(i);
                        continue;
                    }
                    buf.setPosition(i, buf.positionX(i), height, buf.positionZ(i));
                    buf.setVelocity(i, buf.velocityX(i), -buf.velocityY(i) * bounce, buf.positionZ(i));
                }
                i++;
            }
        };
    }

    static SimNode sphere(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var cx = BlockProps.getFloat(properties, "cx", 0f);
        var cy = BlockProps.getFloat(properties, "cy", 0f);
        var cz = BlockProps.getFloat(properties, "cz", 0f);
        var radius = BlockProps.getFloat(properties, "radius", 1f);
        var bounce = BlockProps.getFloat(properties, "bounce", 0.5f);
        return (buf, ctx) -> {
            for (var i = 0; i < buf.count(); i++) {
                var dx = buf.positionX(i) - cx;
                var dy = buf.positionY(i) - cy;
                var dz = buf.positionZ(i) - cz;
                var dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist >= radius || dist < 1e-6f) continue;
                var nx = dx / dist;
                var ny = dy / dist;
                var nz = dz / dist;
                buf.setPosition(i, cx + nx * radius, cy + ny * radius, cz + nz * radius);
                var dot = buf.velocityX(i) * nx + buf.velocityY(i) * ny + buf.velocityZ(i) * nz;
                if (dot < 0f) {
                    buf.setVelocity(i,
                            buf.velocityX(i) - (1f + bounce) * dot * nx,
                            buf.velocityY(i) - (1f + bounce) * dot * ny,
                            buf.velocityZ(i) - (1f + bounce) * dot * nz);
                }
            }
        };
    }

    static SimNode bounds(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var minX = BlockProps.getFloat(properties, "min_x", -10f);
        var minY = BlockProps.getFloat(properties, "min_y", -10f);
        var minZ = BlockProps.getFloat(properties, "min_z", -10f);
        var maxX = BlockProps.getFloat(properties, "max_x", 10f);
        var maxY = BlockProps.getFloat(properties, "max_y", 10f);
        var maxZ = BlockProps.getFloat(properties, "max_z", 10f);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                var x = buf.positionX(i);
                var y = buf.positionY(i);
                var z = buf.positionZ(i);
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }

    static SimNode kill(VfxBlock block, PortValueSource ports) {
        var time = BlockProps.getFloat(block.properties(), "time", 5f);
        return (buf, ctx) -> {
            var i = 0;
            while (i < buf.count()) {
                if (buf.age(i) >= time) {
                    buf.kill(i);
                } else {
                    i++;
                }
            }
        };
    }
}
