package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorRedirectKind;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class VectorRedirectedDamageSource extends DamageSource
        implements VectorRedirectedDamageSourceInfo {
    private final int redirectDepth;
    @Nullable
    private final UUID originalAttackerId;
    private final VectorRedirectKind redirectKind;

    private VectorRedirectedDamageSource(
            DamageSource original,
            ServerPlayer redirector,
            int redirectDepth,
            @Nullable UUID originalAttackerId,
            VectorRedirectKind redirectKind
    ) {
        super(original.typeHolder(), redirector, redirector);
        this.redirectDepth = Math.max(1, redirectDepth);
        this.originalAttackerId = originalAttackerId;
        this.redirectKind = Objects.requireNonNull(redirectKind, "redirectKind");
    }

    public static VectorRedirectedDamageSource from(
            DamageSource original,
            ServerPlayer redirector,
            @Nullable Entity originalAttacker,
            VectorRedirectKind redirectKind
    ) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(redirector, "redirector");
        return new VectorRedirectedDamageSource(
                original,
                redirector,
                1,
                originalAttacker == null ? null : originalAttacker.getUUID(),
                redirectKind
        );
    }

    @Override
    public int redirectDepth() {
        return redirectDepth;
    }

    @Override
    public @Nullable UUID originalAttackerId() {
        return originalAttackerId;
    }

    @Override
    public VectorRedirectKind redirectKind() {
        return redirectKind;
    }

    public ServerPlayer redirector() {
        return (ServerPlayer) getEntity();
    }
}
