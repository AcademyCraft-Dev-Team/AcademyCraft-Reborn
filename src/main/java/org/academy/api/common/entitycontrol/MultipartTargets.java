package org.academy.api.common.entitycontrol;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.entitycontrol.MultipartEntityTargeting;
import org.jspecify.annotations.Nullable;

/** Resolves multipart body segments (for example EnderDragon parts) to the entity that owns them. */
public final class MultipartTargets {
    private MultipartTargets() {
    }

    public static @Nullable Entity resolve(@Nullable Entity entity) {
        return MultipartEntityTargeting.resolve(entity);
    }

    public static @Nullable LivingEntity resolveLiving(@Nullable Entity entity) {
        return MultipartEntityTargeting.resolveLiving(entity);
    }
}
