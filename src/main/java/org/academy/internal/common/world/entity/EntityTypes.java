package org.academy.internal.common.world.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.*;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import static org.academy.AcademyCraft.MODID;

public class EntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownCoin>> THROWN_COIN = ENTITY_TYPES.register("thrown_coin", () -> get(
            ThrownCoin::new, MobCategory.MISC, 0.25F, 0.25F, "thrown_coin"));
    public static final DeferredHolder<EntityType<?>, EntityType<RailgunRay>> RAILGUN_RAY = ENTITY_TYPES.register("railgun_ray", () -> get(
            RailgunRay::new, MobCategory.MISC, 0, 0, "railgun_ray"));
    public static final DeferredHolder<EntityType<?>, EntityType<Arc>> ARC = ENTITY_TYPES.register("arc", () -> get(
            Arc::new, MobCategory.MISC, 0, 0, "arc"));
    public static final DeferredHolder<EntityType<?>, EntityType<HighSpeedElectronBeam>> HIGH_SPEED_ELECTRON_BEAM = ENTITY_TYPES.register("high_speed_electron_beam", () -> get(
            HighSpeedElectronBeam::new, MobCategory.MISC, 0, 0, "high_speed_electron_beam"));
    public static final DeferredHolder<EntityType<?>, EntityType<GlowCircle>> GLOW_CIRCLE = ENTITY_TYPES.register("glow_circle", () -> get(
            GlowCircle::new, MobCategory.MISC, 0, 0, "glow_circle"));
    public static final DeferredHolder<EntityType<?>, EntityType<Smoke>> SMOKE = ENTITY_TYPES.register("smoke", () -> get(
            Smoke::new, MobCategory.MISC, 0, 0, "smoke"));
    public static final DeferredHolder<EntityType<?>, EntityType<CleaningRobot>> CLEANING_ROBOT = ENTITY_TYPES.register("cleaning_robot", () -> get(
            CleaningRobot::new, MobCategory.MISC, 0.5f, 1, "cleaning_robot"));

    public static <T extends Entity> EntityType<T> get(
            EntityType.EntityFactory<T> factory, MobCategory category, float width, float height, String name) {
        return EntityType.Builder.of(factory, category).sized(width, height).build(name);
    }

    private EntityTypes() {}

    public record Type<T extends Entity>(EntityType<T> entityType, String name) {}
}
