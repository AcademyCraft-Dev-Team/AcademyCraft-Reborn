package org.academy.api.common.damage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.api.common.ability.Skill;
import org.jspecify.annotations.Nullable;

/**
 * Maps a skill to its canonical damage type from the skill and category declarations.
 */
public final class SkillDamageTypeResolver {
    private SkillDamageTypeResolver() {
    }

    public static @Nullable ResourceKey<DamageType> resolve(Skill skill) {
        if (skill == null) return null;
        if (skill.getDamageType().isPresent()) return skill.getDamageType().orElseThrow();
        if (skill.getDamageProfile().isPresent()) {
            return AbilityDamageProfile.require(skill.getDamageProfile().orElseThrow()).damageType();
        }
        var category = skill.getCategory();
        if (category.getDefaultDamageType().isPresent()) return category.getDefaultDamageType().orElseThrow();
        if (category.getDefaultDamageProfile().isPresent()) {
            return AbilityDamageProfile.require(category.getDefaultDamageProfile().orElseThrow()).damageType();
        }
        return null;
    }
}
