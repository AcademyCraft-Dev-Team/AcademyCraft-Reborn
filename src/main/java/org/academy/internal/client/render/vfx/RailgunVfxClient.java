package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class RailgunVfxClient {
    private RailgunVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(RailgunRingData.class, VfxPhase.WORLD_TRANSLUCENT, new TexturedQuadRenderer(false));
        VfxManager.INSTANCE.spawn(new RailgunVfx());
    }
}
