package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime;
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
        var queryBounds = bounds.inflate(1.0e-7);
        var entities = ((EntityGetter) this).getEntities(
                source,
                queryBounds,
                predicate
        );
        var level = (Object) this instanceof Level currentLevel ? currentLevel : null;
        var indexedStructures = level == null
                ? List.<Entity>of()
                : BlockStructureCollisionRuntime.query(level, queryBounds);
        var shapes = new ArrayList<VoxelShape>(entities.size() + indexedStructures.size());
        var emittedStructures = indexedStructures.isEmpty()
                ? null
                : java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Entity, Boolean>());
        for (var entity : entities) {
            if (entity instanceof BlockStructureCollision structureCollision) {
                if (emittedStructures != null) emittedStructures.add(entity);
                if (!BlockStructureCollisionRuntime.isIgnored(structureCollision)) {
                    structureCollision.collectCollisionShapes(bounds, shapes::add);
                }
            } else {
                shapes.add(Shapes.create(entity.getBoundingBox()));
            }
        }
        for (var entity : indexedStructures) {
            if (!emittedStructures.add(entity)
                    || entity == source
                    || !predicate.test(entity)) continue;
            var structureCollision = (BlockStructureCollision) entity;
            if (!BlockStructureCollisionRuntime.isIgnored(structureCollision)) {
                structureCollision.collectCollisionShapes(bounds, shapes::add);
            }
        }
        cir.setReturnValue(List.copyOf(shapes));
    }
}
