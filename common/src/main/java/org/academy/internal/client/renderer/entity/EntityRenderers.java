package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.*;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class EntityRenderers {
    public static final Map<EntityType<?>, EntityRendererProvider<?>> RENDERER_MAP = new HashMap<>();

    public static final EntityRendererProvider<ThrownCoin> THROWN_COIN_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.THROWN_COIN_ENTITY_TYPE, ThrownCoinRenderer::new);
    public static final EntityRendererProvider<RailgunRay> RAILGUN_RAY_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.RAILGUN_RAY_ENTITY_TYPE, RailgunRayRenderer::new);
    public static final EntityRendererProvider<Arc> ARC_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.ARC_ENTITY_TYPE, ArcRenderer::new);
    public static final EntityRendererProvider<HighSpeedElectronBeam> HIGH_SPEED_ELECTRON_BEAM_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE, HighSpeedElectronBeamRenderer::new);
    public static final EntityRendererProvider<Plasma> PLASMA_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.PLASMA_ENTITY_TYPE, PlasmaRenderer::new);
    public static final EntityRendererProvider<GlowCircle> GLOW_CIRCLE_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.GLOW_CIRCLE_ENTITY_TYPE, GlowCircleRenderer::new);
    public static final EntityRendererProvider<Smoke> SMOKE_ENTITY_RENDERER_PROVIDER =
            register(EntityTypes.SMOKE_ENTITY_TYPE, SmokeRenderer::new);

    public static <T extends Entity> EntityRendererProvider<T> register(EntityType<T> type, EntityRendererProvider<T> provider) {
        RENDERER_MAP.put(type, provider);
        return provider;
    }

    private EntityRenderers() {
    }
}