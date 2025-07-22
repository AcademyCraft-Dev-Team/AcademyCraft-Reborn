package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.*;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class EntityRenderers {
    public static final Map<EntityType<?>, EntityRendererProvider<?>> RENDERER_MAP = new HashMap<>();

    public static final EntityRendererProvider<ThrownCoin> THROWN_COIN =
            register(EntityTypes.THROWN_COIN, ThrownCoinRenderer::new);
    public static final EntityRendererProvider<RailgunRay> RAILGUN_RAY =
            register(EntityTypes.RAILGUN_RAY, RailgunRayRenderer::new);
    public static final EntityRendererProvider<Arc> ARC =
            register(EntityTypes.ARC, ArcRenderer::new);
    public static final EntityRendererProvider<HighSpeedElectronBeam> HIGH_SPEED_ELECTRON_BEAM =
            register(EntityTypes.HIGH_SPEED_ELECTRON_BEAM, HighSpeedElectronBeamRenderer::new);
    public static final EntityRendererProvider<GlowCircle> GLOW_CIRCLE =
            register(EntityTypes.GLOW_CIRCLE, GlowCircleRenderer::new);
    public static final EntityRendererProvider<Smoke> SMOKE =
            register(EntityTypes.SMOKE, SmokeRenderer::new);
    public static final EntityRendererProvider<CleaningRobot> CLEANING_ROBOT = 
            register(EntityTypes.CLEANING_ROBOT, CleaningRobotRenderer::new);

    public static <T extends Entity> EntityRendererProvider<T> register(EntityType<T> type, EntityRendererProvider<T> provider) {
        net.minecraft.client.renderer.entity.EntityRenderers.register(type, provider);
        return provider;
    }

    public static void init() {
    }

    private EntityRenderers() {
    }
}