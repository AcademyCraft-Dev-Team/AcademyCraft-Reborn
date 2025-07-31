package org.academy.internal.client.renderer.entity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.EntityTypes;

@SuppressWarnings("unused")
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class EntityRenderers {
    @SubscribeEvent
    public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypes.THROWN_COIN.get(), ThrownCoinRenderer::new);
        event.registerEntityRenderer(EntityTypes.RAILGUN_RAY.get(), RailgunRayRenderer::new);
        event.registerEntityRenderer(EntityTypes.ARC.get(), ArcRenderer::new);
        event.registerEntityRenderer(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), HighSpeedElectronBeamRenderer::new);
        event.registerEntityRenderer(EntityTypes.GLOW_CIRCLE.get(), GlowCircleRenderer::new);
        event.registerEntityRenderer(EntityTypes.SMOKE.get(), SmokeRenderer::new);
        event.registerEntityRenderer(EntityTypes.CLEANING_ROBOT.get(), CleaningRobotRenderer::new);
    }

    private EntityRenderers() {
    }
}