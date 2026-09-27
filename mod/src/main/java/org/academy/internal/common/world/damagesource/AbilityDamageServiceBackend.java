package org.academy.internal.common.world.damagesource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.damage.AbilityDamageService;

/**
 * Binds the public {@link AbilityDamageService} facade to the internal skill damage settlement.
 */
public final class AbilityDamageServiceBackend implements AbilityDamageService.Backend {
    @Override
    public boolean apply(ServerPlayer controller, LivingEntity target, Skill skill,
                         ResourceKey<DamageType> type, float amount, float maximumHealthPart) {
        return SkillDamageUtil.apply(controller, target, skill, type, amount, maximumHealthPart);
    }
}
