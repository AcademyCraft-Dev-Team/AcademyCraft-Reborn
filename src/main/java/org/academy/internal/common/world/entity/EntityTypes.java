package org.academy.internal.common.world.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.*;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import static org.academy.AcademyCraft.MODID;

public class EntityTypes {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownCoin>> THROWN_COIN =
            ENTITY_TYPES.registerEntityType(
                    "thrown_coin", ThrownCoin::new, MobCategory.MISC,
                    thrownCoinBuilder ->
                            thrownCoinBuilder.sized(0.25f, 0.25f)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<RailgunRay>> RAILGUN_RAY =
            ENTITY_TYPES.registerEntityType(
                    "railgun_ray", RailgunRay::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<Arc>> ARC =
            ENTITY_TYPES.registerEntityType(
                    "arc", Arc::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<ArcEffect>> ARC_EFFECT =
            ENTITY_TYPES.registerEntityType(
                    "arc_effect", ArcEffect::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<HighSpeedElectronBeam>> HIGH_SPEED_ELECTRON_BEAM =
            ENTITY_TYPES.registerEntityType(
                    "high_speed_electron_beam", HighSpeedElectronBeam::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<LightOrb>> LIGHT_ORB =
            ENTITY_TYPES.registerEntityType(
                    "light_orb", LightOrb::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<GlowCircle>> GLOW_CIRCLE =
            ENTITY_TYPES.registerEntityType(
                    "glow_circle", GlowCircle::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<Smoke>> SMOKE =
            ENTITY_TYPES.registerEntityType(
                    "smoke", Smoke::new, MobCategory.MISC);
    public static final DeferredHolder<EntityType<?>, EntityType<CleaningRobot>> CLEANING_ROBOT =
            ENTITY_TYPES.registerEntityType(
                    "cleaning_robot", CleaningRobot::new, MobCategory.MISC
            );

    private EntityTypes() {}
}