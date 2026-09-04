package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Replaces a structure entity's broad-phase box with its compound collision shapes. */
@Mixin(EntityGetter.class)
public interface MixinEntityGetter {
    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    private void academy$collectBlockStructureCollisions(
            Entity source,
            AABB bounds,
            CallbackInfoReturnable<List<VoxelShape>> cir
    ) {
        if (bounds.getSize() < 1.0e-7) {
            cir.setReturnValue(List.of());
            return;
        }
        Predicate<Entity> predicate = source == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
        var entities = ((EntityGetter) this).getEntities(
                source,
                bounds.inflate(1.0e-7),
                predicate
        );
        if (entities.isEmpty()) {
            cir.setReturnValue(List.of());
            return;
        }
        var shapes = new ArrayList<VoxelShape>(entities.size());
        for (var entity : entities) {
            if (entity instanceof BlockStructureCollision structureCollision) {
                structureCollision.collectCollisionShapes(bounds, shapes::add);
            } else {
                shapes.add(Shapes.create(entity.getBoundingBox()));
            }
        }
        cir.setReturnValue(List.copyOf(shapes));
    }
}
