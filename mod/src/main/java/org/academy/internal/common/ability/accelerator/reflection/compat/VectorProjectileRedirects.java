package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class VectorProjectileRedirects {
    private static final String ORIGINAL_OWNER_PICKUP_TAG =
            "academy_vector_original_owner_pickup";

    private VectorProjectileRedirects() {
    }

    public static VectorProjectileRedirectData get(Projectile projectile) {
        return VectorMotionRedirects.get(projectile);
    }

    public static boolean isRedirected(Projectile projectile) {
        return get(projectile).isRedirected()
                || projectile.getData(AttachmentTypes.VECTOR_REFLECTED_PROJECTILE.get());
    }

    public static VectorProjectileRedirectData mark(
            Projectile projectile,
            ServerPlayer redirector,
            VectorRedirectKind kind
    ) {
        var current = get(projectile);
        VectorAttackAttributionResolver.captureProjectileOwner(projectile);
        var originalOwnerId = current.originalOwnerId() != null
                ? current.originalOwnerId()
                : VectorAttackAttributionResolver.originalProjectileOwnerId(projectile);
        var fingerprint = fingerprint(projectile, originalOwnerId, redirector);
        var data = VectorMotionRedirects.mark(
                projectile,
                originalOwnerId,
                redirector,
                kind,
                fingerprint
        );
        projectile.getPersistentData().putBoolean(ORIGINAL_OWNER_PICKUP_TAG, true);
        projectile.setData(AttachmentTypes.VECTOR_REFLECTED_PROJECTILE.get(), true);
        return data;
    }

    /**
     * Keeps pickup authorization separate from the redirected projectile's current owner. The
     * current owner remains the redirector for damage and attribution, while the original shooter
     * may still recover a landed projectile. The persistent marker keeps this permission across
     * chunk saves without changing the projectile owner stored by vanilla.
     */
    public static boolean allowsOriginalOwnerPickup(Projectile projectile, Entity picker) {
        if (projectile == null || picker == null) return false;
        var data = get(projectile);
        var redirected = data.isRedirected()
                || projectile.getPersistentData()
                .getBoolean(ORIGINAL_OWNER_PICKUP_TAG)
                .orElse(false);
        var originalOwnerId = data.originalOwnerId() != null
                ? data.originalOwnerId()
                : VectorAttackAttributionResolver.originalProjectileOwnerId(projectile);
        return allowsOriginalOwnerPickup(redirected, originalOwnerId, picker.getUUID());
    }

    static boolean allowsOriginalOwnerPickup(
            boolean redirected,
            @Nullable UUID originalOwnerId,
            @Nullable UUID pickerId
    ) {
        return redirected && originalOwnerId != null && originalOwnerId.equals(pickerId);
    }

    static long fingerprint(Projectile projectile, UUID originalOwnerId, ServerPlayer redirector) {
        var value = projectile.level().getGameTime();
        value = 31L * value + projectile.getId();
        value = 31L * value + redirector.getUUID().hashCode();
        value = 31L * value + (originalOwnerId == null ? 0 : originalOwnerId.hashCode());
        return value;
    }
}
