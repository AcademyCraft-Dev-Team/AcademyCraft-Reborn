package org.academy.internal.common.world.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;

import java.util.ArrayList;
import java.util.List;

public class AcademyCraftEntityTypes {
    public static final List<Type<?>> TYPE_LIST = new ArrayList<>();
    public static final EntityType<ThrownCoin> THROWN_COIN_ENTITY_TYPE = EntityType.Builder.of(ThrownCoin::new, MobCategory.MISC).sized(0.5F, 0.5F).build("thrown_coin");
    public static final EntityType<RailgunRay> RAILGUN_RAY_ENTITY_TYPE = EntityType.Builder.of(RailgunRay::new, MobCategory.MISC).sized(0, 0).build("railgun_ray");

    static {
        TYPE_LIST.add(new Type<>(THROWN_COIN_ENTITY_TYPE, "thrown_coin"));
        TYPE_LIST.add(new Type<>(RAILGUN_RAY_ENTITY_TYPE, "railgun_ray"));
    }

    public record Type<T extends Entity>(EntityType<T> entityType, String name) {
    }

    private AcademyCraftEntityTypes() {
    }
}