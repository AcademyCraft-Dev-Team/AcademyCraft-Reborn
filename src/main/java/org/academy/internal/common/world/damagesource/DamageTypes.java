package org.academy.internal.common.world.damagesource;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import org.academy.AcademyCraft;

import java.util.Set;

public final class DamageTypes {
    public static final ResourceKey<DamageType> RAILGUN = ResourceKey.create(Registries.DAMAGE_TYPE, AcademyCraft.academy("railgun"));
    public static final ResourceKey<DamageType> AERO_DAMAGE = create("aerodamage");
    public static final ResourceKey<DamageType> DM_DAMAGE = create("dmdamage");
    public static final ResourceKey<DamageType> ELECTRO_DAMAGE = create("electrodamage");
    public static final ResourceKey<DamageType> MELT_DAMAGE = create("meltdamage");
    public static final ResourceKey<DamageType> SPACE_DAMAGE = create("spacedamage");
    public static final ResourceKey<DamageType> VEC = create("vec");
    public static final ResourceKey<DamageType> CTA = create("cta");

    private static final Set<ResourceKey<DamageType>> ACADEMY_DAMAGE_TYPES = Set.of(
            AERO_DAMAGE, DM_DAMAGE, ELECTRO_DAMAGE, MELT_DAMAGE, SPACE_DAMAGE, VEC, CTA
    );
    private static final Set<ResourceKey<DamageType>> DIRECT_ACTUALLY_HURT_TYPES = Set.of(
            MELT_DAMAGE, SPACE_DAMAGE, VEC, CTA
    );
    private static final Set<ResourceKey<DamageType>> VERIFIED_TRUE_HEALTH_TYPES = Set.of(
            VEC, CTA
    );

    private DamageTypes() {
    }

    public static boolean isAcademyDamage(DamageSource source) {
        return source != null && ACADEMY_DAMAGE_TYPES.stream().anyMatch(source::is);
    }

    public static boolean usesResistanceBackdoor(DamageSource source) {
        return source != null && (source.is(VEC) || source.is(CTA));
    }

    public static boolean usesDirectActuallyHurt(ResourceKey<DamageType> type) {
        return DIRECT_ACTUALLY_HURT_TYPES.contains(type);
    }

    public static boolean usesDirectActuallyHurt(DamageSource source) {
        return source != null && DIRECT_ACTUALLY_HURT_TYPES.stream().anyMatch(source::is);
    }

    public static boolean usesVerifiedTrueHealth(ResourceKey<DamageType> type) {
        return VERIFIED_TRUE_HEALTH_TYPES.contains(type);
    }

    public static boolean usesVerifiedTrueHealth(DamageSource source) {
        return source != null && VERIFIED_TRUE_HEALTH_TYPES.stream().anyMatch(source::is);
    }

    public static boolean isImmunePlayer(Player player, DamageSource source) {
        return player != null && isAcademyDamage(source) && (player.isCreative() || player.isSpectator());
    }

    public static boolean isImmunePlayer(Player player) {
        return player != null && (player.isCreative() || player.isSpectator());
    }

    private static ResourceKey<DamageType> create(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AcademyCraft.academy(name));
    }
}
