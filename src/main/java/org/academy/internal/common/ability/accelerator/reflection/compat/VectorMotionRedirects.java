package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureKinetics;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
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
        var direct = plan.attack().attribution().directEntity();
        var structure = direct instanceof BlockStructure value ? value : null;
        if (structure == null
                && !plan.attack().executionPolicy().safeMotionRedirect()) return false;
        if (direct == null
                || direct instanceof Projectile
                || direct.isRemoved()
                || isRedirected(direct)) {
            return false;
        }
        var speed = direct.getDeltaMovement().length();
        if (!Double.isFinite(speed) || speed < 1.0E-4) return false;
        var direction = plan.redirectedDirection();
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-8) return false;
        direction = direction.normalize();
        var pushDistance = Math.max(
                Math.max(direct.getBoundingBox().getXsize(), direct.getBoundingBox().getZsize()),
                plan.redirector().getBbWidth()
        ) + 0.5;
        var redirectedVelocity = direction.scale(speed);
        var redirectedPosition = plan.mirrorPoint().add(direction.scale(pushDistance));
        if (structure != null) BlockStructureKinetics.stop(structure);
        EntityMotionGuard.runWithMotionSource(plan.redirector(), () -> {
            direct.setDeltaMovement(redirectedVelocity);
            direct.setPos(redirectedPosition);
        });
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
