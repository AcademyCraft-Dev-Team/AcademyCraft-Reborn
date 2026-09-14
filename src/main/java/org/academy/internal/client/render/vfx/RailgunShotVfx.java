package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.client.renderer.entity.ReflectedBeamVisualGeometry;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Maps the server's resolved beam segments to editor-authored visuals on one game clock. */
final class RailgunShotVfx implements Vfx {
    private static final Identifier ASSET = Identifier.fromNamespaceAndPath("academy", "vfxgraph/railgun_shot");
    private final RailgunRay ray;
    private ActiveEffect outgoing;
    private ActiveEffect reflected;
    private float ageSeconds;
    private float detail = 1;
    private boolean stopped;

    RailgunShotVfx(RailgunRay ray) {
        this.ray = ray;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (ray.isRemoved() || ray.level() != Minecraft.getInstance().level) {
            stop();
            return;
        }
        ageSeconds = (ray.tickCount + ctx.partialTick()) / 20f;
        var origin = ray.position();
        var direction = Vec3.directionFromRotation(ray.getXRot(), ray.getYRot()).normalize();
        float originalLength = ReflectedBeamVisualGeometry.safeLength(ray.getBeamLength());
        float length = ray.isReflectionActive()
                ? Math.min(originalLength, ReflectedBeamVisualGeometry.safeLength(ray.getReflectionDistance()))
                : originalLength;
        detail = ctx.camera().pos().distance(origin.toVector3f()) > 72 ? 0.35f : 1;
        if (outgoing == null) outgoing = create(true);
        segment(outgoing, origin, direction, length, ctx.camera().pos());
        if (ray.isReflectionActive()) {
            if (reflected == null) reflected = create(false);
            segment(reflected, origin.add(direction.scale(length)), ray.getReflectionReturnDirection(),
                    ReflectedBeamVisualGeometry.safeLength(ray.getReflectionReturnLength()), ctx.camera().pos());
        } else if (reflected != null) {
            reflected.stop();
            reflected = null;
        }
    }

    private ActiveEffect create(boolean muzzle) {
        var effect = VfxGraphManager.INSTANCE.spawn(ASSET, ray.position().toVector3f());
        effect.bind("time", () -> Value.of(ageSeconds));
        effect.bind("width_scale", () -> Value.of(ray.getBeamWidthMultiplier()));
        effect.bind("seed", () -> Value.of((float) (ray.getUUID().getLeastSignificantBits() & 0xFFFFFFL)));
        effect.bind("detail", () -> Value.of(detail));
        effect.bind("muzzle", () -> Value.of(muzzle ? 1f : 0f));
        effect.setMinimumFarPlane(384);
        effect.setRenderDistance(256);
        return effect;
    }

    private void segment(ActiveEffect effect, Vec3 start, Vec3 direction, float length, Vector3f camera) {
        var unit = direction.normalize().toVector3f();
        boolean valid = unit.isFinite() && unit.lengthSquared() > 1.0e-12f && length > 0;
        effect.setPosition(start.toVector3f());
        if (valid) effect.setRotation(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), unit));
        effect.effect().setLiveParam("length", Value.of(valid ? length : 0));
        float proximity = Math.clamp((3f - camera.distance(start.toVector3f())) / 1.5f, 0, 1);
        effect.effect().setLiveParam("view_near_origin", Value.of(proximity));
        // Include the entire segment, so a distant muzzle cannot cull a beam crossing the camera.
        var center = start.toVector3f().fma(valid ? length * 0.5f : 0, unit);
        effect.setCullingSphere(center, length * 0.5f + 14 * Math.max(1, ray.getBeamWidthMultiplier()));
        effect.setMinimumFarPlane(Math.max(384, length + 256));
    }

    void stop() {
        stopped = true;
        if (outgoing != null) outgoing.stop();
        if (reflected != null) reflected.stop();
    }

    @Override
    public boolean isAlive() {
        return !stopped;
    }
}
