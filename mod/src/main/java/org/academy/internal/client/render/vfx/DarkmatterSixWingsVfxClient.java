package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

public final class DarkmatterSixWingsVfxClient {
    private DarkmatterSixWingsVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(DarkmatterSixWingsData.class, VfxPhase.WORLD_TRANSLUCENT, new DarkmatterSixWingsRenderer());
        VfxManager.INSTANCE.spawn(new DarkmatterSixWingsVfx());
    }
}
