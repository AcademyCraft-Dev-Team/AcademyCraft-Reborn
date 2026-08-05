package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.joml.Vector3f;

public final class SmokeVfx implements Vfx {
    private final Smoke smoke;
    private boolean expired;

    public SmokeVfx(Smoke smoke) {
        this.smoke = smoke;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (smoke.isRemoved() || !smoke.isAlive()) {
            expired = true;
            return;
        }

        var pos = smoke.position();
        var frame = Math.clamp(smoke.frame, 0, 3);
        var col = frame % 2;
        var row = frame / 2;
        var s = 0.5f;
        var u0 = col * s;
        var v0 = row * s;
        var u1 = u0 + s;
        var v1 = v0 + s;

        sink.push(new SmokeData(
                new Vector3f((float) pos.x, (float) pos.y, (float) pos.z),
                smoke.size,
                smoke.getAlpha(),
                u0, v0, u1, v1
        ));
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
