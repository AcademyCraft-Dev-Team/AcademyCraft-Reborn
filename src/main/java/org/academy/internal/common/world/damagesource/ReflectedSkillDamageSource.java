package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class ReflectedSkillDamageSource extends SkillDamageSource
        implements VectorRedirectedDamageSourceInfo {
    private final int reflectionDepth;
    @Nullable
    private final UUID originalAttackerId;

    private ReflectedSkillDamageSource(
            DamageSource original,
            ServerPlayer reflector,
            Skill skill,
            int reflectionDepth,
            @Nullable UUID originalAttackerId
    ) {
        super(original.typeHolder(), reflector, reflector, skill);
        this.reflectionDepth = reflectionDepth;
        this.originalAttackerId = originalAttackerId;
    }

    public static ReflectedSkillDamageSource from(
            DamageSource original,
            ServerPlayer reflector,
            Skill skill,
            @Nullable Entity originalAttacker
    ) {
        return from(original, reflector, skill, originalAttacker, 1);
    }

    public static ReflectedSkillDamageSource from(
            DamageSource original,
            ServerPlayer reflector,
            Skill skill,
            @Nullable Entity originalAttacker,
            int reflectionDepth
    ) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(reflector, "reflector");
        Objects.requireNonNull(skill, "skill");
        return new ReflectedSkillDamageSource(
                original,
                reflector,
                skill,
                Math.max(1, reflectionDepth),
                originalAttacker == null ? null : originalAttacker.getUUID()
        );
    }

    public static boolean isReflected(DamageSource source) {
        return VectorRedirectedDamageSourceInfo.isRedirected(source);
    }

    public int reflectionDepth() {
        return reflectionDepth;
    }

    @Override
    public int redirectDepth() {
        return reflectionDepth;
    }

    public @Nullable UUID originalAttackerId() {
        return originalAttackerId;
    }

    @Override
    public VectorRedirectKind redirectKind() {
        return VectorRedirectKind.REFLECTION;
    }

    public ServerPlayer reflector() {
        return (ServerPlayer) getEntity();
    }

    public boolean shouldTriggerSkillCallbacks() {
        var reflector = reflector();
        return AbilitySystemServer.getSystem(reflector)
                .getPlayerData(reflector.getUUID())
                .isSkillLearned(getSkill().getKeyString());
    }
}
