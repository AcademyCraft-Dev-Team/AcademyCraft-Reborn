package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.sublevel.SubLevelCollisionRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Appends exact SubLevel block shapes to the host level's ordinary block-collision query. */
@Mixin(CollisionGetter.class)
public interface MixinCollisionGetter {
    @ModifyReturnValue(method = "getBlockCollisionsFromContext", at = @At("RETURN"))
    private Iterable<VoxelShape> academy$appendSubLevelCollisions(Iterable<VoxelShape> original,
                                                                   CollisionContext source, AABB bounds) {
        if (bounds.getSize() < 1.0e-7) return original;
        if ((Object) this instanceof Level level) {
            return SubLevelCollisionRuntime.append(level, original, bounds);
        }
        if ((Object) this instanceof PathNavigationRegion) {
            var level = ((PathNavigationRegionAccessor) this).academy$level();
            return SubLevelCollisionRuntime.append(level, original, bounds);
        }
        return original;
    }
}
