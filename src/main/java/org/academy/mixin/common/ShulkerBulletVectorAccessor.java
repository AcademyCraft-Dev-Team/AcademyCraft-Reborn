package org.academy.mixin.common;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShulkerBullet.class)
public interface ShulkerBulletVectorAccessor {
    @Accessor("finalTarget")
    void academy$setFinalTarget(@Nullable EntityReference<Entity> target);

    @Accessor("currentMoveDirection")
    @Nullable Direction academy$getCurrentMoveDirection();

    @Invoker("selectNextMoveDirection")
    void academy$selectNextMoveDirection(
            Direction.@Nullable Axis avoidAxis,
            @Nullable Entity target
    );
}
