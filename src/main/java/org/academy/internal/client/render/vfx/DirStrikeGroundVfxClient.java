package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class DirStrikeGroundVfxClient {
    private DirStrikeGroundVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(DirStrikeGroundData.class, VfxPhase.WORLD_TRANSLUCENT, new DirStrikeGroundRenderer());
        VfxManager.INSTANCE.spawn(new DirStrikeGroundEffect());
    }
}
