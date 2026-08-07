package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class VectorMotionRedirects {
    private VectorMotionRedirects() {
    }

    public static VectorProjectileRedirectData get(Entity entity) {
        return entity.getData(AttachmentTypes.VECTOR_PROJECTILE_REDIRECT.get());
    }

    public static boolean isRedirected(Entity entity) {
        return entity != null && get(entity).isRedirected();
    }

    public static VectorProjectileRedirectData mark(
            Entity entity,
            @Nullable UUID originalOwnerId,
            ServerPlayer redirector,
            VectorRedirectKind kind,
            long fingerprint
    ) {
        var current = get(entity);
        var data = new VectorProjectileRedirectData(
                Math.max(1, current.redirectDepth() + 1),
                current.originalOwnerId() == null ? originalOwnerId : current.originalOwnerId(),
                redirector.getUUID(),
                kind,
                fingerprint
        );
        entity.setData(AttachmentTypes.VECTOR_PROJECTILE_REDIRECT.get(), data);
        return data;
    }

    public static boolean redirectProfiledEntity(VectorRedirectPlan plan) {
        if (!plan.attack().executionPolicy().safeMotionRedirect()) return false;
        var direct = plan.attack().attribution().directEntity();
        if (direct == null
                || direct instanceof net.minecraft.world.entity.projectile.Projectile
                || direct.isRemoved()
                || isRedirected(direct)) {
            return false;
        }
        var speed = direct.getDeltaMovement().length();
        if (!Double.isFinite(speed) || speed < 1.0E-4) return false;
        var direction = plan.redirectedDirection();
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-8) return false;
        direction = direction.normalize();
        var pushDistance = Math.max(direct.getBbWidth(), plan.redirector().getBbWidth()) + 0.5;
        direct.setDeltaMovement(direction.scale(speed));
        direct.setPos(plan.mirrorPoint().add(direction.scale(pushDistance)));
        direct.hurtMarked = true;
        direct.needsSync = true;
        direct.syncPosition = true;
        var originalAttacker = plan.attack().attribution().originalAttacker();
        mark(
                direct,
                originalAttacker == null ? null : originalAttacker.getUUID(),
                plan.redirector(),
                plan.kind(),
                plan.attack().fingerprint()
        );
        return true;
    }
}
