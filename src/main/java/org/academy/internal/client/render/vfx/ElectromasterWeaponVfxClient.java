package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.renderer.RendererManager;

public final class ElectromasterWeaponVfxClient {
    private ElectromasterWeaponVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(
                ElectromasterWeaponData.class,
                VfxPhase.WORLD_TRANSLUCENT,
                new TexturedQuadRenderer(false)
        );
        RendererManager.registerEffectRenderer(ElectromasterWeaponFirstPersonBridge.INSTANCE);
        VfxManager.INSTANCE.spawn(new ElectromasterWeaponVfx());
    }
}
