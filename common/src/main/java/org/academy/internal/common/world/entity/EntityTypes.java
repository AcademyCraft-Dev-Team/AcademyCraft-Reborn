package org.academy.internal.common.world.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.Arc;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.academy.internal.common.world.entity.skill.RailgunRay;

import java.util.ArrayList;
import java.util.List;

public class EntityTypes {
    public static final List<Type<?>> TYPE_LIST = new ArrayList<>();
    public static final EntityType<ThrownCoin> THROWN_COIN_ENTITY_TYPE = EntityType.Builder.of(
            ThrownCoin::new, MobCategory.MISC
    ).sized(0.5F, 0.5F).build("thrown_coin");
    public static final EntityType<RailgunRay> RAILGUN_RAY_ENTITY_TYPE = EntityType.Builder.of(
            RailgunRay::new, MobCategory.MISC
    ).sized(0, 0).build("railgun_ray");
    public static final EntityType<Arc> ARC_ENTITY_TYPE = EntityType.Builder.<Arc>of(
            Arc::new, MobCategory.MISC
    ).sized(0, 0).build("arc");
    public static final EntityType<HighSpeedElectronBeam> HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE = EntityType.Builder.of(
            HighSpeedElectronBeam::new, MobCategory.MISC
    ).sized(0, 0).build("high_speed_electron_beam");
    public static final EntityType<Plasma> PLASMA_ENTITY_TYPE = EntityType.Builder.of(
            Plasma::new, MobCategory.MISC
    ).sized(0, 0).build("plasma");

    static {
        TYPE_LIST.add(new Type<>(THROWN_COIN_ENTITY_TYPE, "thrown_coin"));
        TYPE_LIST.add(new Type<>(RAILGUN_RAY_ENTITY_TYPE, "railgun_ray"));
        TYPE_LIST.add(new Type<>(ARC_ENTITY_TYPE, "arc"));
        TYPE_LIST.add(new Type<>(HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE, "high_speed_electron_beam"));
        TYPE_LIST.add(new Type<>(PLASMA_ENTITY_TYPE, "plasma"));
    }

    public record Type<T extends Entity>(EntityType<T> entityType, String name) {
    }

    private EntityTypes() {
    }
}