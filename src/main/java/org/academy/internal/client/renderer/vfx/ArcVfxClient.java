package org.academy.internal.client.renderer.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.ArcEffect;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ArcVfxClient {
    private ArcVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(ArcCoreData.class, VfxPhase.WORLD_TRANSLUCENT, new ArcRenderer(false));
        VfxRegistry.register(ArcGlowData.class, VfxPhase.WORLD_GLOW, new ArcRenderer(true));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof ArcEffect arcEffect) {
            VfxManager.INSTANCE.spawn(new ArcEffectVfx(arcEffect));
        }
    }
}
