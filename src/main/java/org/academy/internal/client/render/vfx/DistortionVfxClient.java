package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class DistortionVfxClient {
    private DistortionVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(DistortionData.class, VfxPhase.WORLD_TRANSLUCENT, new TexturedQuadRenderer(false));
        VfxManager.INSTANCE.spawn(new DistortionVfx());
    }
}
