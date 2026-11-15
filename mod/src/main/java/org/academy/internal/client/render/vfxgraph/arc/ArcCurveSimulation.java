package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.curve.CurveBuffer;
import org.academy.internal.client.render.vfxgraph.arc.CurveGenerator;
import org.academy.internal.client.render.vfxgraph.arc.NoiseAnimator;
import org.academy.internal.client.render.vfxgraph.arc.SurfaceConstraint;
import org.academy.api.client.render.vfxgraph.sim.CurveSimulation;
import org.academy.api.client.render.vfxgraph.sim.SimContext;
import org.joml.Vector3f;

import java.util.Random;

public final class ArcCurveSimulation implements CurveSimulation {
    private static final float NOISE_SCALE = 2.0f;
    private static final long NOISE_SEED = 42L;

    private final SurfaceConstraint surfaceConstraint = new SurfaceConstraint();
    private final Vector3f anchorPoint = new Vector3f();
    private final Vector3f anchorNormal = new Vector3f();

    @Override
    public void simulate(SimContext context, CurveBuffer buffer, Config config) {
        var dt = context.dt();
        var time = context.time();
        var random = context.random();
        for (var i = 0; i < buffer.count(); i++) {
            var current = buffer.arc(i);
            if (!(current instanceof ArcCurve arc)) continue;
            if (arc.sparkVelocity() != null) {
                var v = arc.sparkVelocity();
                float gx = 0f, gy = 0f, gz = -0.9f;
                var vx = v[0] + gx * dt;
                var vy = v[1] + gy * dt;
                var vz = v[2] + gz * dt;
                v[0] = vx;
                v[1] = vy;
                v[2] = vz;
                var len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                var ux = len < 1e-6f ? 0f : vx / len;
                var uy = len < 1e-6f ? 1f : vy / len;
                var uz = len < 1e-6f ? 0f : vz / len;
                var half = arc.size() >= 2 ? 0.5f * dist(arc, 0, 1) : 0f;
                var cx = (arc.x(0) + arc.x(arc.size() - 1)) * 0.5f;
                var cy = (arc.y(0) + arc.y(arc.size() - 1)) * 0.5f;
                var cz = (arc.z(0) + arc.z(arc.size() - 1)) * 0.5f;
                cx += vx * dt;
                cy += vy * dt;
                cz += vz * dt;
                arc.setPoint(0, cx - ux * half, cy - uy * half, cz - uz * half);
                arc.setPoint(arc.size() - 1, cx + ux * half, cy + uy * half, cz + uz * half);
                continue;
            }
            if (arc.hasArchBase()) {
                if (arc.hasAnchor()) {
                    reanchorSurfaceArc(arc, dt, random, config.driftSpeed());
                } else {
                    wanderArcBase(arc, random, config.driftSpeed());
                }
                CurveGenerator.sampleSurfaceArch(arc);
            }
            var strength = arc.hasNoiseStrength() ? arc.noiseStrength() : config.noiseStrength();
            if (strength > 1e-6f) {
                NoiseAnimator.animate(arc, time, config.driftSpeed(), strength, NOISE_SCALE,
                        NOISE_SEED + arc.seed() * 7919L);
            }
            if (arc.hasSurface() || arc.hasAnchor()) {
                surfaceConstraint.constrain(arc);
            }
        }
    }

    private static float dist(ArcCurve arc, int a, int b) {
        var dx = arc.x(b) - arc.x(a);
        var dy = arc.y(b) - arc.y(a);
        var dz = arc.z(b) - arc.z(a);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void reanchorSurfaceArc(ArcCurve arc, float dt, Random random, float driftSpeed) {
        var sampler = arc.anchorSampler();
        if (sampler == null) return;
        var stepScale = Math.clamp(dt * 60f, 0f, 2f);
        var step = (0.01f + 0.02f * random.nextFloat())
                * (arc.hasDriftSpeed() ? arc.driftSpeed() : driftSpeed) * stepScale;
        var angle = random.nextFloat() * (float) (Math.PI * 2.0);
        arc.accumulateAnchorWander((float) Math.cos(angle) * step, (float) Math.sin(angle) * step);
        var u = Math.clamp(arc.anchorU() + arc.anchorWanderU(), 0.03f, 0.97f);
        var v = Math.clamp(arc.anchorV() + arc.anchorWanderV(), 0.03f, 0.97f);
        if (!sampler.sample(arc.anchorIndex(), u, v, anchorPoint, anchorNormal)) return;
        arc.reanchorSurface(anchorPoint.x, anchorPoint.y, anchorPoint.z,
                anchorNormal.x, anchorNormal.y, anchorNormal.z);
    }

    private void wanderArcBase(ArcCurve arc, Random random, float driftSpeed) {
        float nx = arc.archNx(), ny = arc.archNy(), nz = arc.archNz();
        var rl = 0f;
        float rx = 0f, ry = 0f, rz = 0f;
        for (var guard = 0; guard < 4; guard++) {
            rx = random.nextFloat() * 2f - 1f;
            ry = random.nextFloat() * 2f - 1f;
            rz = random.nextFloat() * 2f - 1f;
            rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl > 1e-6f) break;
        }
        if (rl < 1e-6f) return;
        rx /= rl;
        ry /= rl;
        rz /= rl;
        var cx = ry * nz - rz * ny;
        var cy = rz * nx - rx * nz;
        var cz = rx * ny - ry * nx;
        var cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6f) return;
        cx /= cl;
        cy /= cl;
        cz /= cl;
        var step = (0.01f + 0.02f * random.nextFloat())
                * (arc.hasDriftSpeed() ? arc.driftSpeed() : driftSpeed);
        arc.accumulateWander(cx * step, cy * step, cz * step);
    }
}
