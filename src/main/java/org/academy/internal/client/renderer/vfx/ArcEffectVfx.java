package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.common.world.entity.skill.ArcEffect;

import java.util.ArrayList;
import java.util.List;

public final class ArcEffectVfx implements Vfx {
    private final ArcEffect arc;
    private final List<ArcTube> tubes = new ArrayList<>();
    private boolean expired;

    public ArcEffectVfx(ArcEffect arc) {
        this.arc = arc;
    }

    @Override
    public void update(float dt, VfxFrameContext ctx) {
        if (arc.isRemoved() || !arc.isAlive()) {
            expired = true;
        }
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (expired) return;
        var paths = arc.getArcPaths();
        if (paths.isEmpty()) return;
        var time = arc.tickCount - 1.0f + ctx.partialTick();

        while (tubes.size() < paths.size()) {
            tubes.add(new ArcTube());
        }
        while (tubes.size() > paths.size()) {
            tubes.removeLast();
        }

        for (var i = 0; i < paths.size(); i++) {
            var tube = tubes.get(i);
            tube.build(paths.get(i), time);
            if (tube.mesh().isEmpty()) continue;
            sink.push(new LightningCoreData(tube));
            sink.push(new LightningRenderData(tube));
        }
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }
}
