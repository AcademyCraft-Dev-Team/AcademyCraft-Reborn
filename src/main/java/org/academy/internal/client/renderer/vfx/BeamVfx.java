package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.joml.Vector3f;

public final class BeamVfx implements Vfx {
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

        var isCharging = beam.isCharging();
        float progress;
        if (isCharging) {
            progress = (beam.currentChargerTicks + ctx.partialTick()) / HighSpeedElectronBeam.MAX_CHARGE_TICKS;
        } else {
            progress = (beam.currentRayLifeTicks - ctx.partialTick()) / HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS;
        }
        progress = Math.clamp(progress, 0.0f, 1.0f);

        var pos = beam.position();
        var pos3 = new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        var yRot = beam.getYRot();
        var xRot = beam.getXRot();
        var length = beam.length;

        sink.push(new BeamCoreData(pos3, yRot, xRot, length, progress, isCharging));
        sink.push(new BeamGlowData(pos3, yRot, xRot, length, progress, isCharging));
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
