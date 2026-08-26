package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.academy.internal.common.world.entity.EntityTypes;

@EventBusSubscriber(Dist.CLIENT)
public final class EntityRenderers {
    private EntityRenderers() {
    }

    @SubscribeEvent
    public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypes.THROWN_COIN.get(), ThrownCoinRenderer::new);
        event.registerEntityRenderer(EntityTypes.RAILGUN_RAY.get(), RailgunRayRenderer::new);
        event.registerEntityRenderer(EntityTypes.PLASMA.get(), PlasmaRenderer::new);
        event.registerEntityRenderer(EntityTypes.ARC.get(), ArcRenderer::new);
        event.registerEntityRenderer(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), HighSpeedElectronBeamRenderer::new);
        event.registerEntityRenderer(EntityTypes.MAGNETIC_WEAPON_BLADE.get(), MagneticWeaponBladeRenderer::new);
        event.registerEntityRenderer(EntityTypes.LIGHT_ORB.get(), LightOrbRenderer::new);
        event.registerEntityRenderer(EntityTypes.GLOW_CIRCLE.get(), GlowCircleRenderer::new);
        event.registerEntityRenderer(EntityTypes.KINETIC_SHOCKWAVE.get(), KineticShockwaveRenderer::new);
        event.registerEntityRenderer(EntityTypes.SMOKE.get(), SmokeRenderer::new);
        event.registerEntityRenderer(EntityTypes.CLEANING_ROBOT.get(), CleaningRobotRenderer::new);
        event.registerEntityRenderer(EntityTypes.ARC_EFFECT.get(), ArcEffectRenderer::new);
        event.registerEntityRenderer(EntityTypes.DARKMATTER_CUT_SLASH.get(), DarkmatterCutSlashRenderer::new);
        event.registerEntityRenderer(EntityTypes.DARKMATTER_FEATHER_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.45f, true));
        event.registerEntityRenderer(EntityTypes.DARKMATTER_CREATURE_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 0.35f, true));
        event.registerEntityRenderer(EntityTypes.DARKMATTER_SPEAR_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 1.25f, true));
        event.registerEntityRenderer(EntityTypes.DARKMATTER_BEETLE.get(), DarkmatterBeetleRenderer::new);
    }
}
