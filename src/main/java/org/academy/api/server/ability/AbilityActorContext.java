package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;

import java.util.Objects;

/** Immutable cast parameters; non-player callers supply their own authoritative account. */
public record AbilityActorContext(LivingEntity subject, Skill skill, double abilityPower,
                                  double damageMultiplier, int milestone, AbilityResourceAccount resource) {
    public AbilityActorContext {
        Objects.requireNonNull(subject);
        Objects.requireNonNull(skill);
        Objects.requireNonNull(resource);
        if (!(subject.level() instanceof ServerLevel)
                || !Double.isFinite(abilityPower) || abilityPower < 0
                || !Double.isFinite(damageMultiplier) || damageMultiplier < 0) {
            throw new IllegalArgumentException("Invalid server ability actor");
        }
        milestone = Math.clamp(milestone, 0, 3);
    }

    public ServerLevel level() { return (ServerLevel) subject.level(); }

    public static AbilityActorContext of(ServerPlayer player, Skill skill, AbilityResourceAccount resource) {
        var system = AbilitySystemServer.getSystem(player);
        return new AbilityActorContext(player, skill, system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                system.getPlayerDamageMultiplier(player.getUUID()), skill.getEffectiveProficiencyMilestone(player), resource);
    }
}
