package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.renderer.RendererManager;

public final class WingVfxClient {
    private WingVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(WingData.class, VfxPhase.WORLD_TRANSLUCENT, new WingRenderer());
        RendererManager.registerEffectRenderer(WingFirstPersonBridge.INSTANCE);
        VfxManager.INSTANCE.spawn(new WingVfx());
    }
}
