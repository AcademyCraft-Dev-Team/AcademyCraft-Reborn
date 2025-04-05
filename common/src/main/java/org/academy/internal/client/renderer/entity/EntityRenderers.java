package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.Arc;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.academy.internal.common.world.entity.skill.RailgunRay;

import java.util.ArrayList;
import java.util.List;

public class EntityRenderers {
    public static final List<Renderer<?>> RENDERER_LIST = new ArrayList<>();
    public static final EntityRendererProvider<ThrownCoin> THROWN_COIN_ENTITY_RENDERER_PROVIDER = ThrownCoinRenderer::new;
    public static final EntityRendererProvider<RailgunRay> RAILGUN_RAY_ENTITY_RENDERER_PROVIDER = RailgunRayRenderer::new;
    public static final EntityRendererProvider<Arc> ARC_ENTITY_RENDERER_PROVIDER = ArcRenderer::new;
    public static final EntityRendererProvider<HighSpeedElectronBeam> HIGH_SPEED_ELECTRON_BEAM_ENTITY_RENDERER_PROVIDER = HighSpeedElectronBeamRenderer::new;
    public static final EntityRendererProvider<Plasma> PLASMA_ENTITY_RENDERER_PROVIDER = PlasmaRenderer::new;

    static {
        RENDERER_LIST.add(new Renderer<>(EntityTypes.RAILGUN_RAY_ENTITY_TYPE, RAILGUN_RAY_ENTITY_RENDERER_PROVIDER));
        RENDERER_LIST.add(new Renderer<>(EntityTypes.THROWN_COIN_ENTITY_TYPE, THROWN_COIN_ENTITY_RENDERER_PROVIDER));
        RENDERER_LIST.add(new Renderer<>(EntityTypes.ARC_ENTITY_TYPE, ARC_ENTITY_RENDERER_PROVIDER));
        RENDERER_LIST.add(new Renderer<>(EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE, HIGH_SPEED_ELECTRON_BEAM_ENTITY_RENDERER_PROVIDER));
        RENDERER_LIST.add(new Renderer<>(EntityTypes.PLASMA_ENTITY_TYPE, PLASMA_ENTITY_RENDERER_PROVIDER));
    }

    public record Renderer<T extends Entity>(EntityType<T> entityType, EntityRendererProvider<T> entityRenderer) {
    }

    private EntityRenderers() {
    }
}