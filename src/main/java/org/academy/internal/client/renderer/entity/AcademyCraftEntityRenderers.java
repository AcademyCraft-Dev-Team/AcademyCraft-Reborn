package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.RailgunRay;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftEntityRenderers {
    public static final List<Renderer<?>> RENDERER_LIST = new ArrayList<>();
    public static final EntityRendererProvider<ThrownCoin> THROWN_COIN_ENTITY_RENDERER_PROVIDER = new EntityRendererProvider<>() {
        @Override
        public @NotNull EntityRenderer<ThrownCoin> create(@NotNull Context context) {
            return new ThrownCoinRenderer(context);
        }
    };
    public static final EntityRendererProvider<RailgunRay> RAILGUN_RAY_ENTITY_RENDERER_PROVIDER = new EntityRendererProvider<>() {
        @Override
        public @NotNull EntityRenderer<RailgunRay> create(@NotNull Context context) {
            return new RailgunRayRenderer(context);
        }
    };

    static {
        RENDERER_LIST.add(new Renderer<>(AcademyCraftEntityTypes.RAILGUN_RAY_ENTITY_TYPE, RAILGUN_RAY_ENTITY_RENDERER_PROVIDER));
        RENDERER_LIST.add(new Renderer<>(AcademyCraftEntityTypes.THROWN_COIN_ENTITY_TYPE, THROWN_COIN_ENTITY_RENDERER_PROVIDER));
    }

    public record Renderer<T extends Entity>(EntityType<T> entityType, EntityRendererProvider<T> entityRenderer) {
    }

    private AcademyCraftEntityRenderers() {
    }
}