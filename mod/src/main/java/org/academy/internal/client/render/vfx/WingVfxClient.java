package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class WingVfxClient {
    private WingVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(WingData.class, VfxPhase.WORLD_TRANSLUCENT, new WingRenderer());
        VfxManager.INSTANCE.spawn(new WingVfx());
    }
}
