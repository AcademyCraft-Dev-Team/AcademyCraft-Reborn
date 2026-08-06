package org.academy.internal.client.renderer.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.Plasma;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PlasmaVfxClient {
    private PlasmaVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(PlasmaCloudData.class, VfxPhase.WORLD_AFTER_SKY, new PlasmaCloudRenderer());
        VfxRegistry.register(PlasmaCoreData.class, VfxPhase.WORLD_GLOW, new PlasmaCoreRenderer());
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        VfxManager.INSTANCE.renderAfterSkyFrame();
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Plasma plasma) {
            VfxManager.INSTANCE.spawn(new PlasmaVfx(plasma));
        }
    }
}
