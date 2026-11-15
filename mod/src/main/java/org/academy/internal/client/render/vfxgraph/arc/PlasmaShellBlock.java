package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.nodes.BlockProps;
import org.academy.api.client.render.vfxgraph.nodes.BlockSim;
import org.academy.api.client.render.vfxgraph.nodes.PortValueSource;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class PlasmaShellBlock {
    private PlasmaShellBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var positionParam = BlockProps.getString(properties, "position_param", "");
        var emissionParam = BlockProps.getString(properties, "emission_param", "");
        var progressParam = BlockProps.getString(properties, "progress_param", "");
        var radius = BlockProps.getFloat(properties, "radius", 8.45f);
        var radiusMin = BlockProps.getFloat(properties, "radius_min", 0.42f);
        var radiusMax = BlockProps.getFloat(properties, "radius_max", radius);
        var radiusPower = Math.max(0.1f, BlockProps.getFloat(properties, "radius_power", 2.3f));
        var duration = BlockProps.getFloat(properties, "duration", 0f);
        var count = Math.max(1, BlockProps.getInt(properties, "count", 7));
        var segments = Math.max(8, BlockProps.getInt(properties, "segments", 22));
        var rotationSpeed = BlockProps.getFloat(properties, "rotation_speed", 0.72f);
        var jitterAmount = Math.max(0f, BlockProps.getFloat(properties, "jitter", 0.022f));
        var arcSpanMin = Math.max(0.04f, BlockProps.getFloat(properties, "arc_span_min", 0.16f));
        var arcSpanMax = Math.max(arcSpanMin, BlockProps.getFloat(properties, "arc_span_max", 0.38f));
        var surfaceOffset = Math.max(0f, BlockProps.getFloat(properties, "surface_offset", 0.22f));
        var flickerRate = Math.max(1f, BlockProps.getFloat(properties, "flicker_rate", 12f));
        var width = BlockProps.getFloat(properties, "width", 0.045f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 0.025f);
        var color = BlockProps.getColor(properties, "color");
        long[] seed = {0L};
        var transientGroup = ArcSupport.newTransientGroup();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(transientGroup);
            var emission = BlockSim.clamp01(ctx.paramFloat(emissionParam, 1f));
            if (duration > 0f) {
                emission *= 1f - BlockSim.clamp01(ctx.time() / duration);
            }
            if (emission <= 0.001f) return;
            var cx = ctx.paramVec3(positionParam, 0, 0f);
            var cy = ctx.paramVec3(positionParam, 1, 0f);
            var cz = ctx.paramVec3(positionParam, 2, 0f);
            var shellProgress = BlockSim.clamp01(ctx.paramFloat(progressParam, 1f));
            var liveRadius = progressParam.isEmpty()
                    ? radius
                    : BlockSim.lerp(radiusMin, radiusMax, (float) Math.pow(shellProgress, radiusPower));
            var spin = ctx.time() * rotationSpeed;
            var flickerFrame = (float) Math.floor(ctx.time() * flickerRate);
            var shellRadius = liveRadius + surfaceOffset * (0.35f + shellProgress * 0.65f);
            for (var index = 0; index < count; index++) {
                if (BlockSim.hash(index + 0.71f, flickerFrame + 3.19f, 5.83f) < 0.16f) continue;

                var normalY = BlockSim.lerp(-0.82f, 0.82f, BlockSim.hash(index + 1.7f, 19.3f, 2.1f));
                var normalAngle = spin * (0.56f + index * 0.07f)
                        + BlockSim.hash(index + 2.3f, 23.7f, 1.9f) * (float) (Math.PI * 2.0);
                var normalHorizontal = (float) Math.sqrt(1f - normalY * normalY);
                var nx = (float) Math.cos(normalAngle) * normalHorizontal;
                var ny = normalY;
                var nz = (float) Math.sin(normalAngle) * normalHorizontal;
                var horizontalLength = Math.max(1.0e-4f, (float) Math.sqrt(nx * nx + nz * nz));
                var ux = nz / horizontalLength;
                var uy = 0f;
                var uz = -nx / horizontalLength;
                var vx = ny * uz;
                var vy = nz * ux - nx * uz;
                var vz = -ny * ux;
                var spanFraction = BlockSim.lerp(arcSpanMin, arcSpanMax,
                        BlockSim.hash(index + 7.1f, flickerFrame + 2.7f, 11.9f));
                var arcSpan = (float) (Math.PI * 2.0) * spanFraction;
                var startAngle = spin * (1.08f + index * 0.035f)
                        + BlockSim.hash(index + 13.4f, flickerFrame + 0.43f, 17.2f)
                        * (float) (Math.PI * 2.0);
                var liveSegments = Math.max(7, Math.round(segments * spanFraction / arcSpanMax));
                var arc = ArcSupport.add(ctx.arcs(), transientGroup);
                for (var point = 0; point <= liveSegments; point++) {
                    var u = (float) point / liveSegments;
                    var angle = startAngle + arcSpan * u;
                    var cos = (float) Math.cos(angle);
                    var sin = (float) Math.sin(angle);
                    var qx = ux * cos + vx * sin;
                    var qy = uy * cos + vy * sin;
                    var qz = uz * cos + vz * sin;
                    var tx = -ux * sin + vx * cos;
                    var ty = -uy * sin + vy * cos;
                    var tz = -uz * sin + vz * cos;
                    var tangentJolt = (BlockSim.hash(index * 31.7f + point, flickerFrame * 17.3f, 29.1f) * 2f - 1f)
                            * jitterAmount;
                    var crossJolt = (BlockSim.hash(index * 43.1f + point, flickerFrame * 23.9f, 37.7f) * 2f - 1f)
                            * jitterAmount * 0.82f;
                    var sx = qx + tx * tangentJolt + nx * crossJolt;
                    var sy = qy + ty * tangentJolt + ny * crossJolt;
                    var sz = qz + tz * tangentJolt + nz * crossJolt;
                    var invLength = 1f / Math.max(1.0e-4f, (float) Math.sqrt(sx * sx + sy * sy + sz * sz));
                    var radialCrackle = shellRadius * jitterAmount * 0.055f
                            * (BlockSim.hash(point + 5.2f, index + flickerFrame * 3.1f, 41.3f) * 2f - 1f);
                    var taper = (float) Math.pow(Math.sin(u * Math.PI), 0.34);
                    arc.addPoint(
                            cx + sx * invLength * (shellRadius + radialCrackle),
                            cy + sy * invLength * (shellRadius + radialCrackle),
                            cz + sz * invLength * (shellRadius + radialCrackle),
                            width * (0.18f + taper * 0.82f), 0f);
                }
                var flash = 0.78f + BlockSim.hash(index + 3.6f, flickerFrame + 8.2f, 47.5f) * 0.34f;
                ArcSupport.configure(arc, color, emission * flash, lifetime, ++seed[0]);
                arc.setNoiseStrength(jitterAmount * 0.32f);
            }
        };
    }
}
