package org.academy.internal.client.render.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.Smoke;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SmokeVfxClient {
    private SmokeVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(SmokeData.class, VfxPhase.WORLD_TRANSLUCENT, new SmokeRenderer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof Smoke smoke) {
            VfxManager.INSTANCE.spawn(new SmokeVfx(smoke));
        }
    }
}
