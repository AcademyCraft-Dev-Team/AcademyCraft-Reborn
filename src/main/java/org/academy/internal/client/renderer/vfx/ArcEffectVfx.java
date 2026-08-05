package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.client.renderer.arc.PathProcessor;
import org.academy.internal.common.world.entity.skill.ArcEffect;

public final class ArcEffectVfx implements Vfx {
    private final ArcEffect arc;
    private boolean expired;

    public ArcEffectVfx(ArcEffect arc) {
        this.arc = arc;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (arc.isRemoved() || !arc.isAlive()) {
            expired = true;
            return;
        }

        var paths = arc.getArcPaths();
        if (paths.isEmpty()) return;

        var cameraPos = ctx.camera().pos();
        var time = arc.tickCount - 1.0f + ctx.partialTick();

        for (var path : paths) {
            var renderData = PathProcessor.process(path, time, cameraPos);
            if (renderData.quads.isEmpty() && renderData.branches.isEmpty()) continue;
            sink.push(new ArcCoreData(renderData));
            sink.push(new ArcGlowData(renderData));
        }
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
