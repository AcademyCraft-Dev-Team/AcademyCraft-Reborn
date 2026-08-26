package org.academy.internal.common.world.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.academy.internal.common.world.entity.projectile.DarkmatterCreatureProjectile;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.academy.internal.common.world.entity.projectile.DarkmatterSpearProjectile;
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
    public static final DeferredHolder<EntityType<?>, EntityType<Plasma>> PLASMA =
            ENTITY_TYPES.registerEntityType(
                    "plasma", Plasma::new, MobCategory.MISC,
                    builder -> builder.sized(1.0f, 1.0f).clientTrackingRange(160).updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<Arc>> ARC =
            ENTITY_TYPES.registerEntityType(
                    "arc", Arc::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<ArcEffect>> ARC_EFFECT =
            ENTITY_TYPES.registerEntityType(
                    "arc_effect", ArcEffect::new, MobCategory.MISC,
                    builder -> builder.sized(0.1f, 0.1f).clientTrackingRange(64).updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<HighSpeedElectronBeam>> HIGH_SPEED_ELECTRON_BEAM =
            ENTITY_TYPES.registerEntityType(
                    "high_speed_electron_beam", HighSpeedElectronBeam::new, MobCategory.MISC,
                    builder -> builder.clientTrackingRange(10).updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<MagneticWeaponBlade>> MAGNETIC_WEAPON_BLADE =
            ENTITY_TYPES.registerEntityType(
                    "magnetic_weapon_blade", MagneticWeaponBlade::new, MobCategory.MISC,
                    builder -> builder.sized(0.25f, 0.25f).clientTrackingRange(48).updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<LightOrb>> LIGHT_ORB =
            ENTITY_TYPES.registerEntityType(
                    "light_orb", LightOrb::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<GlowCircle>> GLOW_CIRCLE =
            ENTITY_TYPES.registerEntityType(
                    "glow_circle", GlowCircle::new, MobCategory.MISC
            );
    public static final DeferredHolder<EntityType<?>, EntityType<KineticShockwave>> KINETIC_SHOCKWAVE =
            ENTITY_TYPES.registerEntityType(
                    "kinetic_shockwave", KineticShockwave::new, MobCategory.MISC,
                    builder -> builder.sized(0.1f, 0.1f).clientTrackingRange(64).updateInterval(1)
            );
    public static final DeferredHolder<EntityType<?>, EntityType<Smoke>> SMOKE =
            ENTITY_TYPES.registerEntityType(
                    "smoke", Smoke::new, MobCategory.MISC);
    public static final DeferredHolder<EntityType<?>, EntityType<DarkmatterCutSlash>> DARKMATTER_CUT_SLASH =
            ENTITY_TYPES.registerEntityType(
                    "darkmatter_cut_slash", DarkmatterCutSlash::new, MobCategory.MISC,
                    builder -> builder.sized(0.1f, 0.1f).clientTrackingRange(64).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DarkmatterFeatherProjectile>> DARKMATTER_FEATHER_PROJECTILE =
            ENTITY_TYPES.registerEntityType(
                    "darkmatter_feather_projectile", DarkmatterFeatherProjectile::new, MobCategory.MISC,
                    builder -> builder.sized(0.18f, 0.18f).clientTrackingRange(64).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DarkmatterCreatureProjectile>> DARKMATTER_CREATURE_PROJECTILE =
            ENTITY_TYPES.registerEntityType(
                    "darkmatter_creature_projectile", DarkmatterCreatureProjectile::new, MobCategory.MISC,
                    builder -> builder.sized(0.22f, 0.22f).clientTrackingRange(96).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DarkmatterSpearProjectile>> DARKMATTER_SPEAR_PROJECTILE =
            ENTITY_TYPES.registerEntityType(
                    "darkmatter_spear_projectile", DarkmatterSpearProjectile::new, MobCategory.MISC,
                    builder -> builder.sized(0.25f, 0.25f).clientTrackingRange(64).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<DarkmatterBeetle>> DARKMATTER_BEETLE =
            ENTITY_TYPES.registerEntityType(
                    "darkmatter_beetle", DarkmatterBeetle::new, MobCategory.MISC,
                    builder -> builder.sized(0.55f, 0.4f).clientTrackingRange(48).updateInterval(2));
    public static final DeferredHolder<EntityType<?>, EntityType<CleaningRobot>> CLEANING_ROBOT =
            ENTITY_TYPES.registerEntityType(
                    "cleaning_robot", CleaningRobot::new, MobCategory.MISC
            );

    private EntityTypes() {
    }
}
