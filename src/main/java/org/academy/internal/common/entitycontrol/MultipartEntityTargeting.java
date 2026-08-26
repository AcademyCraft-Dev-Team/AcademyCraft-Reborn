package org.academy.internal.common.entitycontrol;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a hit multipart body segment to the logical entity that owns it.
 */
public final class MultipartEntityTargeting {
    private MultipartEntityTargeting() {
    }

    public static @Nullable Entity resolve(@Nullable Entity entity) {
        var resolved = entity;
        while (resolved instanceof PartEntity<?> part) {
            var parent = part.getParent();
            if (parent == null || parent == resolved) break;
            resolved = parent;
        }
        return resolved;
    }

    public static @Nullable LivingEntity resolveLiving(@Nullable Entity entity) {
        var resolved = resolve(entity);
        return resolved instanceof LivingEntity living ? living : null;
    }
}
