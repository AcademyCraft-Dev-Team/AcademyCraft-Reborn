package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.attachment.AttachmentTypes;

public final class VectorProjectileRedirects {
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
        var owner = projectile.getOwner();
        var originalOwnerId = current.originalOwnerId() != null
                ? current.originalOwnerId()
                : owner == null ? null : owner.getUUID();
        var fingerprint = fingerprint(projectile, originalOwnerId, redirector);
        var data = VectorMotionRedirects.mark(
                projectile,
                originalOwnerId,
                redirector,
                kind,
                fingerprint
        );
        projectile.setData(AttachmentTypes.VECTOR_REFLECTED_PROJECTILE.get(), true);
        return data;
    }

    static long fingerprint(Projectile projectile, java.util.UUID originalOwnerId, ServerPlayer redirector) {
        var value = projectile.level().getGameTime();
        value = 31L * value + projectile.getId();
        value = 31L * value + redirector.getUUID().hashCode();
        value = 31L * value + (originalOwnerId == null ? 0 : originalOwnerId.hashCode());
        return value;
    }
}
