package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds a coarse SubLevel hint to vanilla path navigation only. Ordinary block collision must not
 * contain structure AABBs: entities resolve the real oriented cuboids through the entity movement
 * mixin, otherwise hollow or rotated shapes would block space the structure does not occupy.
 */
@Mixin(CollisionGetter.class)
public interface MixinCollisionGetter {
    @ModifyReturnValue(method = "getBlockCollisionsFromContext", at = @At("RETURN"))
    private Iterable<VoxelShape> academy$appendSubLevelNavigation(Iterable<VoxelShape> original,
                                                                   CollisionContext source, AABB bounds) {
        if (bounds.getSize() < 1.0e-7) return original;
        if ((Object) this instanceof PathNavigationRegion) {
            var level = ((PathNavigationRegionAccessor) this).academy$level();
            return SubLevelCollisionRuntime.appendNavigation(level, original, bounds);
        }
        return original;
    }
}
