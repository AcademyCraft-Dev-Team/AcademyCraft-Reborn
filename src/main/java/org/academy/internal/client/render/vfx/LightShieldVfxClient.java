package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.renderer.RendererManager;

public final class LightShieldVfxClient {
    private LightShieldVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(LightShieldQuadData.class, VfxPhase.WORLD_TRANSLUCENT, new TexturedQuadRenderer(false));
        RendererManager.registerEffectRenderer(LightShieldFirstPersonBridge.INSTANCE);
        VfxManager.INSTANCE.spawn(new LightShieldVfx());
    }
}
