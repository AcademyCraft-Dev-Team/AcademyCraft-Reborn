package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class TrailVfxClient {
    private TrailVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(ColorMeshData.class, VfxPhase.WORLD_TRANSLUCENT, new ColorMeshRenderer());
        VfxManager.INSTANCE.spawn(TrailVfx.INSTANCE);
    }
}
