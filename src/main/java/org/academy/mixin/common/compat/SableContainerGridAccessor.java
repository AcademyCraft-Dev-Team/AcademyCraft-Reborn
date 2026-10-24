package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubLevelContainer.class, remap = false)
public interface SableContainerGridAccessor {
    @Accessor("originX") int academy$originX();
    @Accessor("originZ") int academy$originZ();
}
