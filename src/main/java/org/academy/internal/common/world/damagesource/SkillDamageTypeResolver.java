package org.academy.internal.common.world.damagesource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.aeromanip.Aeromanip;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.ability.meltdowner.Meltdowner;
import org.academy.internal.common.ability.teleport.Teleport;
import org.jetbrains.annotations.Nullable;

/**
 * Maps category-owned skills to their canonical damage type.
 */
public final class SkillDamageTypeResolver {
    private SkillDamageTypeResolver() {
    }

    public static @Nullable ResourceKey<DamageType> resolve(Skill skill) {
        if (skill == null) return null;
        return switch (skill.getCategory()) {
            case Aeromanip ignored -> DamageTypes.AERO_DAMAGE;
            case Darkmatter ignored -> DamageTypes.DM_DAMAGE;
            case Electromaster ignored -> DamageTypes.ELECTRO_DAMAGE;
            case Meltdowner ignored -> DamageTypes.MELT_DAMAGE;
            case Teleport ignored -> DamageTypes.SPACE_DAMAGE;
            default -> null;
        };
    }
}
