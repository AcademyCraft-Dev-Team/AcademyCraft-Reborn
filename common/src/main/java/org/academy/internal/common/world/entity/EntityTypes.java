package org.academy.internal.common.world.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.*;
import org.academy.internal.common.world.entity.vehicle.CleaningRobot;

import java.util.ArrayList;
import java.util.List;

public class EntityTypes {
    public static final List<Type<?>> TYPE_LIST = new ArrayList<>();

    public static final EntityType<ThrownCoin> THROWN_COIN = register(
            ThrownCoin::new, MobCategory.MISC, 0.25F, 0.25F, "thrown_coin");
    public static final EntityType<RailgunRay> RAILGUN_RAY = register(
            RailgunRay::new, MobCategory.MISC, 0, 0, "railgun_ray");
    public static final EntityType<Arc> ARC = register(
            Arc::new, MobCategory.MISC, 0, 0, "arc");
    public static final EntityType<HighSpeedElectronBeam> HIGH_SPEED_ELECTRON_BEAM = register(
            HighSpeedElectronBeam::new, MobCategory.MISC, 0, 0, "high_speed_electron_beam");
    public static final EntityType<Plasma> PLASMA = register(
            Plasma::new, MobCategory.MISC, 0, 0, "plasma");
    public static final EntityType<GlowCircle> GLOW_CIRCLE = register(
            GlowCircle::new, MobCategory.MISC, 0, 0, "glow_circle");
    public static final EntityType<Smoke> SMOKE = register(
            Smoke::new, MobCategory.MISC, 0, 0, "smoke");
    public static final EntityType<CleaningRobot> CLEANING_ROBOT = register(
            CleaningRobot::new, MobCategory.MISC, 0.5f, 1, "cleaning_robot"
    );

    public static <T extends Entity> EntityType<T> register(
            EntityType.EntityFactory<T> factory, MobCategory category, float width, float height, String name) {
        var entityType = EntityType.Builder.of(factory, category).sized(width, height).build(name);
        TYPE_LIST.add(new Type<>(entityType, name));
        return entityType;
    }

    private EntityTypes() {}

    public record Type<T extends Entity>(EntityType<T> entityType, String name) {}
}
