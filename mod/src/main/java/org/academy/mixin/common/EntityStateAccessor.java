package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityStateAccessor {
    @Accessor("removalReason")
    @Nullable Entity.RemovalReason academy$getRemovalReason();

    @Accessor("removalReason")
    void academy$setRemovalReason(@Nullable Entity.RemovalReason reason);
}
