package org.academy.internal.common.ability.meltdowner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.util.LevelUtil;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.*;
import org.academy.internal.common.ability.meltdowner.skills.lv1.RadiationIntensify;

import java.util.function.Predicate;

public final class MeltdownerBeamActions {
    private MeltdownerBeamActions() {
    }

    public static void damageAlong(
            ServerLevel level,
            ServerPlayer player,
            Skill skill,
            Vec3 start,
            Vec3 end,
            float radius,
            float baseDamage,
            float maxHealthRatio,
            float abilityPower,
            float playerMultiplier,
            boolean radiationEnabled
    ) {
        var payload = createPayload(
                level,
                player,
                skill,
                radius,
                baseDamage,
                maxHealthRatio,
                abilityPower,
                playerMultiplier,
                radiationEnabled
        );
        var attack = LinearReflectionResolver.resolve(level, new LinearSegment(start, end), payload);
        LinearAttackExecutor.execute(level, attack, payload);
    }

    public static LinearAttackPayload createPayload(
            ServerLevel level,
            ServerPlayer player,
            Skill skill,
            float radius,
            float baseDamage,
            float maxHealthRatio,
            float abilityPower,
            float playerMultiplier,
            boolean radiationEnabled
    ) {
        return createPayload(
                level,
                player,
                skill,
                radius,
                baseDamage,
                maxHealthRatio,
                abilityPower,
                playerMultiplier,
                radiationEnabled,
                target -> target instanceof LivingEntity
        );
    }

    public static LinearAttackPayload createPayload(
            ServerLevel level,
            ServerPlayer player,
            Skill skill,
            float radius,
            float baseDamage,
            float maxHealthRatio,
            float abilityPower,
            float playerMultiplier,
            boolean radiationEnabled,
            Predicate<Entity> targetFilter
    ) {
        var source = SkillDamageSource.of(player, skill);
        return LinearAttackPayload.builder(player, skill, source, radius)
                .targetFilter(targetFilter)
                .outboundTargetFilter(target -> !player.isAlliedTo(target))
                .damage(target -> {
                    var living = target instanceof LivingEntity entity ? entity : null;
                    var marked = radiationEnabled
                            && living != null
                            && RadiationIntensify.isMarked(living, level.getGameTime());
                    var markMultiplier = Skills.RADIATION_INTENSIFY.get().hasProficiencyMilestone(player, 2)
                            ? 1.6f : RadiationIntensify.MARK_DAMAGE_MULTIPLIER;
                    return MeltdownerBeamDamage.calculatePowerScaledBase(
                            baseDamage,
                            maxHealthRatio,
                            living == null ? 0.0f : living.getMaxHealth(),
                            abilityPower,
                            playerMultiplier,
                            marked,
                            markMultiplier
                    );
                })
                .onHit((target, reflected, hurt) -> {
                    if (hurt && radiationEnabled && target instanceof LivingEntity living) {
                        RadiationIntensify.mark(player, living, level.getGameTime());
                    }
                })
                .build();
    }

    public static LinearAttackExecutor.ExecutionResult damageAlong(
            ServerLevel level,
            ResolvedLinearAttack attack,
            LinearAttackPayload payload
    ) {
        return LinearAttackExecutor.execute(level, attack, payload);
    }

    public static void destroyBlocksAlongSegment(
            ServerLevel level,
            LinearSegment segment,
            float radius,
            int miningLevel,
            boolean dropBlocks,
            boolean spawnParticles,
            boolean canBlock,
            ServerPlayer breaker
    ) {
        executeBlocksAlongSegment(
                level,
                segment,
                radius,
                miningLevel,
                dropBlocks,
                spawnParticles,
                canBlock,
                false,
                breaker
        );
    }

    public static double executeBlocksAlongSegment(
            ServerLevel level,
            LinearSegment segment,
            float radius,
            int miningLevel,
            boolean dropBlocks,
            boolean spawnParticles,
            boolean canBlock,
            boolean simulate,
            ServerPlayer breaker
    ) {
        var result = LevelUtil.destroyBlocksAlongPath(
                level,
                segment.start(),
                segment.end(),
                radius,
                miningLevel,
                dropBlocks,
                spawnParticles,
                canBlock,
                simulate,
                breaker
        );
        return Mth.clamp(result.getValue(), 0.0, segment.length());
    }
}
