package org.academy.api.server.damage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.AbilityDamageProfile;
import org.academy.internal.common.world.damagesource.AbilityDamageProfiles;
import org.academy.internal.common.world.damagesource.SkillDamageTypeResolver;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * Damage settlement for an already authorized skill cast. The caller owns cast/maintenance costs;
 * this method does not charge again for each target or damage pulse. No fallback hit is retried.
 */
public final class AbilityDamageService {
    private AbilityDamageService() {
    }

    public record Request(float amount, float maximumHealthPart,
                          @Nullable ResourceKey<DamageType> damageType,
                          @Nullable ResourceKey<AbilityDamageProfile> damageProfile) {
        public Request {
            if (!Float.isFinite(amount) || amount <= 0 || !Float.isFinite(maximumHealthPart)
                    || maximumHealthPart < 0 || maximumHealthPart > amount) {
                throw new IllegalArgumentException("Invalid damage composition");
            }
            if (damageType != null && damageProfile != null) throw new IllegalArgumentException("Choose type or profile");
        }
        public static Request of(float amount) { return new Request(amount, 0, null, null); }
        public Request withType(ResourceKey<DamageType> type) {
            return new Request(amount, maximumHealthPart, Objects.requireNonNull(type), null);
        }
        public Request withProfile(ResourceKey<AbilityDamageProfile> profile) {
            return new Request(amount, maximumHealthPart, null, Objects.requireNonNull(profile));
        }
    }

    public record Result(boolean applied, float healthLost, float absorptionLost) {
    }

    public static Result apply(ServerPlayer controller, LivingEntity target, Skill skill, Request request) {
        Objects.requireNonNull(controller);
        Objects.requireNonNull(target);
        Objects.requireNonNull(skill);
        Objects.requireNonNull(request);
        if (!controller.level().getServer().isSameThread()) {
            throw new IllegalStateException("Ability damage requires the server thread");
        }
        if (controller.level() != target.level()) return new Result(false, 0, 0);
        var type = request.damageType() != null ? request.damageType()
                : request.damageProfile() != null ? AbilityDamageProfiles.require(request.damageProfile()).damageType()
                : SkillDamageTypeResolver.resolve(skill);
        if (type == null) type = net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK;
        controller.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(type);
        var health = target.getHealth();
        var absorption = target.getAbsorptionAmount();
        var applied = SkillDamageUtil.apply(controller, target, skill, type, request.amount(), request.maximumHealthPart());
        return new Result(applied, Math.max(0, health - target.getHealth()),
                Math.max(0, absorption - target.getAbsorptionAmount()));
    }
}
