package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.client.renderer.entity.ReflectedBeamVisualGeometry;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class BeamVfx implements Vfx {
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final double DIRECTION_EPSILON_SQUARED = 1.0e-12;

    private final HighSpeedElectronBeam beam;
    private boolean expired;

    public BeamVfx(HighSpeedElectronBeam beam) {
        this.beam = beam;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (beam.isRemoved() || !beam.isAlive()) {
            expired = true;
            return;
        }

        boolean isCharging;
        float progress;
        if (beam.isContinuous()) {
            isCharging = false;
            progress = 1.0f;
        } else if (!beam.hasFired()) {
            isCharging = true;
            progress = beam.isHeldCharge() && beam.getAttackDelayTicks() == 0
                    ? 1.0f
                    : (beam.currentChargerTicks + ctx.partialTick())
                    / Math.max(1.0f, beam.getAttackDelayTicks());
        } else {
            isCharging = false;
            progress = (beam.currentRayLifeTicks - ctx.partialTick()) / HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS;
        }
        progress = Math.clamp(progress, 0.0f, 1.0f);

        var origin = beam.position();
        var logicalDirection = Vec3.directionFromRotation(beam.getXRot(), beam.getYRot());
        var visualStart = origin;
        var visualSideOffset = beam.getVisualSideOffset();
        if (Float.isFinite(visualSideOffset) && Math.abs(visualSideOffset) > 1.0e-4f) {
            var horizontalForward = Vec3.directionFromRotation(0.0f, beam.getYRot());
            var right = horizontalForward.cross(WORLD_UP);
            if (right.lengthSqr() > DIRECTION_EPSILON_SQUARED) {
                visualStart = origin.add(right.normalize().scale(visualSideOffset));
            }
        }

        var originalLength = ReflectedBeamVisualGeometry.safeLength(beam.getBeamLength());
        var visualEnd = origin.add(logicalDirection.scale(originalLength));
        var widthScale = Float.isFinite(beam.getBeamScale()) ? Math.max(0.0f, beam.getBeamScale()) : 0.0f;
        if (!beam.isReflectionActive()) {
            pushSegment(sink, visualStart, visualEnd, progress, isCharging, widthScale, 1.0f);
            return;
        }

        var reflectedLength = Math.clamp(beam.getReflectionDistance(), 0.0f, originalLength);
        var reflectionPoint = origin.add(logicalDirection.scale(reflectedLength));
        var returnEnd = ReflectedBeamVisualGeometry.fullReturnEnd(
                reflectionPoint,
                logicalDirection,
                beam.getReflectionReturnLength()
        );
        pushSegment(sink, visualStart, reflectionPoint, progress, isCharging, widthScale, 1.0f);
        pushSegment(sink, reflectionPoint, returnEnd, progress, false, widthScale * 0.9f, 0.8f);
    }

    private static void pushSegment(
            VfxSink sink,
            Vec3 start,
            Vec3 end,
            float progress,
            boolean isCharging,
            float widthScale,
            float ballScale
    ) {
        var direction = end.subtract(start);
        var length = direction.length();
        float yRot = 0.0f;
        float xRot = 0.0f;
        if (direction.lengthSqr() > DIRECTION_EPSILON_SQUARED && Double.isFinite(length)) {
            var horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            yRot = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            xRot = (float) Math.toDegrees(Math.atan2(-direction.y, horizontalLength));
        } else {
            length = 0.0;
        }

        var pos = new Vector3f((float) start.x, (float) start.y, (float) start.z);
        var safeLength = (float) Math.max(0.0, length);
        sink.push(new BeamCoreData(
                pos, yRot, xRot, safeLength, progress, isCharging, widthScale, ballScale
        ));
        sink.push(new BeamGlowData(
                pos, yRot, xRot, safeLength, progress, isCharging, widthScale, ballScale
        ));
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
