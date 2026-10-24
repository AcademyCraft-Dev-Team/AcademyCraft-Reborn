package org.academy.mixin.common;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the host level of a PathNavigationRegion collision query. */
@Mixin(PathNavigationRegion.class)
public interface PathNavigationRegionAccessor {
    @Accessor("level")
    Level academy$level();
}
