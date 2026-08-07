package org.academy.internal.common.world.damagesource;

import net.minecraft.world.damagesource.DamageSource;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface VectorRedirectedDamageSourceInfo {
    int redirectDepth();

    @Nullable UUID originalAttackerId();

    VectorRedirectKind redirectKind();

    static boolean isRedirected(DamageSource source) {
        return source instanceof VectorRedirectedDamageSourceInfo redirected
                && redirected.redirectDepth() > 0;
    }
}
