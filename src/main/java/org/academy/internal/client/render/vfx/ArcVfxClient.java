package org.academy.internal.client.render.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ArcVfxClient {
    private ArcVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(LightningCoreData.class, VfxPhase.WORLD_TRANSLUCENT, new LightningRenderer(false));
        VfxRegistry.register(LightningRenderData.class, VfxPhase.WORLD_GLOW, new LightningRenderer(true));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof ArcEffect arcEffect) {
            VfxManager.INSTANCE.spawn(new ArcEffectVfx(arcEffect));
        } else if (entity instanceof MagneticWeaponBlade blade) {
            VfxManager.INSTANCE.spawn(new MagneticWeaponBladeArcVfx(blade));
        }
    }
}
