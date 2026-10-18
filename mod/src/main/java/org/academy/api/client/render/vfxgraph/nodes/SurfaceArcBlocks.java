package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.arc.*;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.MeshAssets;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class SurfaceArcBlocks {
    private SurfaceArcBlocks() {
    }

    static SimNode surface(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var ox = BlockProps.getFloat(properties, "origin_x", 0f);
        var oy = BlockProps.getFloat(properties, "origin_y", 0f);
        var oz = BlockProps.getFloat(properties, "origin_z", 0f);
        var mesh = BlockProps.getString(properties, "mesh", "builtin:plane");
        var density = BlockProps.getFloat(properties, "density", 3.8f);
        var probability = BlockProps.getFloat(properties, "probability", 0.5f);
        var frequency = BlockProps.getFloat(properties, "frequency", 1f);
        var framePeriod = BlockProps.getInt(properties, "frame_period", 3);
        var fps = BlockProps.getFloat(properties, "fps", 30f);
        var height = BlockProps.getFloat(properties, "height", 1f);
        var curve = BlockProps.getFloat(properties, "curve", 0.78f);
        var width = BlockProps.getFloat(properties, "width", 0.01f);
        var segments = BlockProps.getInt(properties, "segments", 12);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var color = BlockProps.getColor(properties, "color");
        var emission = BlockProps.getFloat(properties, "emission", 1f);
        var noiseStrength = BlockProps.getFloat(properties, "noise_strength", 0.5f);
        var driftSpeed = BlockProps.getFloat(properties, "drift_speed", 1.5f);
        var base = MeshAssets.resolve(mesh);
        if (base == null) return OutputBlocks.noop(block, ports);
        var surface = offsetTriangles(base, ox, oy, oz);
        var distributor = new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
            var frame = (long) (ctx.time() * fps);
            if (frequency > 0f) {
                if (frame % framePeriod != 0) return;
                if (frame == lastGateFrame[0]) return;
            }
            lastGateFrame[0] = frame;
            seed[0]++;
            var samples = distributor.distribute(density, probability, ctx.time(), frequency, seed[0]);
            for (var s : samples) {
                var arc = ctx.arcs().add();
                CurveGenerator.generateSurfaceArc(
                        arc,
                        s.x(), s.y(), s.z(),
                        s.nx(), s.ny(), s.nz(),
                        height, curve, width, segments,
                        color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                        lifetime, seed[0] * 31 + (long) (s.x() * 1000f));
                arc.setSurface(surface);
                arc.setNoiseStrength(noiseStrength);
                arc.setDriftSpeed(driftSpeed);
            }
        };
    }

    static SimNode contact(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var ox = BlockProps.getFloat(properties, "origin_x", 0f);
        var oy = BlockProps.getFloat(properties, "origin_y", 0f);
        var oz = BlockProps.getFloat(properties, "origin_z", 0f);
        var mesh = BlockProps.getString(properties, "mesh", "builtin:plane");
        var contactMesh = BlockProps.getString(properties, "contact_mesh", "builtin:sphere");
        var contactRange = BlockProps.getFloat(properties, "contact_range", 4.1f);
        var cox = BlockProps.getFloat(properties, "contact_origin_x", 0f);
        var coy = BlockProps.getFloat(properties, "contact_origin_y", 4.3f);
        var coz = BlockProps.getFloat(properties, "contact_origin_z", 0f);
        var density = BlockProps.getFloat(properties, "density", 3.8f);
        var probability = BlockProps.getFloat(properties, "probability", 0.5f);
        var frequency = BlockProps.getFloat(properties, "frequency", 1f);
        var framePeriod = BlockProps.getInt(properties, "frame_period", 3);
        var fps = BlockProps.getFloat(properties, "fps", 30f);
        var height = BlockProps.getFloat(properties, "height", 1f);
        var curve = BlockProps.getFloat(properties, "curve", 0.78f);
        var width = BlockProps.getFloat(properties, "width", 0.01f);
        var segments = BlockProps.getInt(properties, "segments", 12);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 1f);
        var color = BlockProps.getColor(properties, "color");
        var emission = BlockProps.getFloat(properties, "emission", 1f);
        var noiseStrength = BlockProps.getFloat(properties, "noise_strength", 0.5f);
        var driftSpeed = BlockProps.getFloat(properties, "drift_speed", 1.5f);
        var base = MeshAssets.resolve(mesh);
        var contactBase = MeshAssets.resolve(contactMesh);
        if (base == null || contactBase == null) return OutputBlocks.noop(block, ports);
        var surface = offsetTriangles(base, ox, oy, oz);
        var contact = offsetTriangles(contactBase, cox, coy, coz);
        var distributor = new SurfaceDistributor(surface);
        long[] seed = {0L};
        long[] lastGateFrame = {Long.MIN_VALUE};
        return (buf, ctx) -> {
            var frame = (long) (ctx.time() * fps);
            if (frequency > 0f) {
                if (frame % framePeriod != 0) return;
                if (frame == lastGateFrame[0]) return;
            }
            lastGateFrame[0] = frame;
            seed[0]++;
            var samples = distributor.distribute(density, probability, ctx.time(), frequency, seed[0]);
            for (var s : samples) {
                float px = s.x(), py = s.y(), pz = s.z();
                var dist = MeshDistance.nearestDistance(contact, px, py, pz);
                if (dist > contactRange) continue;
                var nearest = MeshDistance.nearestPoint(contact, px, py, pz);
                var arc = ctx.arcs().add();
                CurveGenerator.generateContactArc(
                        arc, px, py, pz,
                        nearest[0], nearest[1], nearest[2],
                        width, segments,
                        color[0] * emission, color[1] * emission, color[2] * emission, color[3],
                        lifetime, seed[0] * 97 + (long) (s.x() * 1000f));
                arc.setSurface(contact);
                arc.setNoiseStrength(noiseStrength);
                arc.setDriftSpeed(driftSpeed);
            }
        };
    }

    static SimNode spark(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var probability = BlockProps.getFloat(properties, "probability", 0.5f);
        var maxSparks = BlockProps.getInt(properties, "max_sparks", 3);
        var splashSpeed = BlockProps.getFloat(properties, "splash_speed", 1.3f);
        var lifetime = BlockProps.getFloat(properties, "lifetime", 0.5f);
        var scale = BlockProps.getFloat(properties, "scale", 1.4f);
        var radius = BlockProps.getFloat(properties, "radius", 0.005f);
        var color = BlockProps.getColor(properties, "color");
        var emission = BlockProps.getFloat(properties, "emission", 1f);
        long[] seed = {0L};
        return (buf, ctx) -> {
            seed[0]++;
            var random = ctx.random();
            var arcs = ctx.arcs();
            var n = arcs.count();
            for (var a = 0; a < n; a++) {
                var arc = arcs.arc(a);
                if (!arc.fresh() || !arc.hasSurface()) continue;
                var size = arc.size();
                if (size < 2) continue;
                var spawned = 0;
                var pointCount = Math.min(size, 10);
                for (var i = 0; i < pointCount; i++) {
                    if (spawned >= maxSparks) break;
                    if (random.nextFloat() > probability) continue;
                    var idx = i * (size - 1) / Math.max(1, pointCount - 1);
                    float px = arc.x(idx), py = arc.y(idx), pz = arc.z(idx);
                    var ref = surfaceTangent(arc, idx);
                    var dir = SurfaceDistributor.tangentDirection(ref[0], ref[1], ref[2],
                            (float) Math.PI / 3f, random);
                    var speed = splashSpeed * (0.3f + 0.9f * random.nextFloat());
                    var vx = dir[0] * speed;
                    var vy = dir[1] * speed;
                    var vz = dir[2] * speed;
                    var lifeScale = BlenderArcCurves.sample(BlenderArcCurves.PARTICLE_LIFE, 0f);
                    var len = (0.01f + 0.02f * random.nextFloat()) * scale * lifeScale;
                    var vlen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    var ux = vlen < 1e-6f ? 0f : vx / vlen;
                    var uy = vlen < 1e-6f ? 1f : vy / vlen;
                    var uz = vlen < 1e-6f ? 0f : vz / vlen;
                    var spark = arcs.add();
                    var ex = px + ux * len;
                    var ey = py + uy * len;
                    var ez = pz + uz * len;
                    spark.addPoint(px, py, pz, radius * (0.2f + 0.4f * random.nextFloat()), 0, 0);
                    spark.addPoint(ex, ey, ez, radius * 0.3f, 0, 0);
                    spark.setColor(color[0] * emission, color[1] * emission, color[2] * emission, color[3]);
                    spark.setLifetime(lifetime);
                    spark.setSeed(seed[0] * 131 + a * 17L + i);
                    spark.setSparkVelocity(vx, vy, vz);
                    spawned++;
                }
            }
        };
    }

    private static float[] surfaceTangent(ArcCurve arc, int i) {
        var prev = Math.max(0, i - 1);
        var next = Math.min(arc.size() - 1, i + 1);
        var tx = arc.x(next) - arc.x(prev);
        var ty = arc.y(next) - arc.y(prev);
        var tz = arc.z(next) - arc.z(prev);
        var len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1e-6f) return new float[]{0, 1, 0};
        return new float[]{tx / len, ty / len, tz / len};
    }

    private static float[] offsetTriangles(float[] tris, float ox, float oy, float oz) {
        if (tris.length == 0) return tris;
        var out = tris.clone();
        for (var i = 0; i + 2 < out.length; i += 3) {
            out[i] += ox;
            out[i + 1] += oy;
            out[i + 2] += oz;
        }
        return out;
    }
}
