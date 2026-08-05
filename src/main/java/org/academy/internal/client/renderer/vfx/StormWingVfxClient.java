package org.academy.internal.client.renderer.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StormWingVfxClient {
    private StormWingVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(StormWingData.class, VfxPhase.WORLD_TRANSLUCENT, new StormWingRenderer());
        VfxManager.INSTANCE.spawn(new StormWingVfx());
    }
}
