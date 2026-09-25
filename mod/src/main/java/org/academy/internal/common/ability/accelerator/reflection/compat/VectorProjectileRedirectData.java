package org.academy.internal.common.ability.accelerator.reflection.compat;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record VectorProjectileRedirectData(
        int redirectDepth,
        @Nullable UUID originalOwnerId,
        @Nullable UUID redirectorId,
        VectorRedirectKind kind,
        long fingerprint
) {
    private static final VectorProjectileRedirectData NONE = new VectorProjectileRedirectData(
            0,
            null,
            null,
            VectorRedirectKind.REFLECTION,
            0L
    );

    public VectorProjectileRedirectData {
        redirectDepth = Math.max(0, redirectDepth);
        kind = kind == null ? VectorRedirectKind.REFLECTION : kind;
    }

    public static VectorProjectileRedirectData none() {
        return NONE;
    }

    public boolean isRedirected() {
        return redirectDepth > 0;
    }
}
