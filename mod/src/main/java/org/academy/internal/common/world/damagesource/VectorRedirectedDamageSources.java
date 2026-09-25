package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.jetbrains.annotations.Nullable;

public final class VectorRedirectedDamageSources {
    private VectorRedirectedDamageSources() {
    }

    public static DamageSource from(
            DamageSource original,
            ServerPlayer redirector,
            @Nullable Entity originalAttacker,
            VectorRedirectKind kind
    ) {
        if (kind == VectorRedirectKind.REFLECTION && original instanceof SkillDamageSource skillSource) {
            return ReflectedSkillDamageSource.from(
                    original,
                    redirector,
                    skillSource.getSkill(),
                    originalAttacker
            );
        }
        return VectorRedirectedDamageSource.from(original, redirector, originalAttacker, kind);
    }
}
