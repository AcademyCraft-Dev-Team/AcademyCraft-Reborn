package org.academy.internal.client.render.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class BeamVfxClient {
    private BeamVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(BeamCoreData.class, VfxPhase.WORLD_TRANSLUCENT, new BeamRenderer(false));
        VfxRegistry.register(BeamGlowData.class, VfxPhase.WORLD_GLOW, new BeamRenderer(true));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof HighSpeedElectronBeam beam) {
            VfxManager.INSTANCE.spawn(new BeamVfx(beam));
        }
    }
}
