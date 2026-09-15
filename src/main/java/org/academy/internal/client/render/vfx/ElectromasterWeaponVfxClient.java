package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.renderer.RendererManager;

public final class ElectromasterWeaponVfxClient {
    private ElectromasterWeaponVfxClient() {
    }

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        VfxRegistry.register(
                ElectromasterWeaponData.class,
                VfxPhase.WORLD_TRANSLUCENT,
                new TexturedQuadRenderer(false)
        );
        RendererManager.registerEffectRenderer(ElectromasterWeaponFirstPersonBridge.INSTANCE);
        VfxManager.INSTANCE.spawn(new ElectromasterWeaponVfx());
    }
}
