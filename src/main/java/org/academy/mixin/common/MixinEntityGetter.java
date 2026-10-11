package org.academy.mixin.common;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.BlockStructureCollision;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime;
import org.academy.internal.common.structure.BlockStructureCollisionRuntime.CollisionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/** Replaces a structure entity's broad-phase box with its compound collision shapes. */
@Mixin(EntityGetter.class)
public interface MixinEntityGetter {
    @WrapOperation(
            method = "getEntityCollisions",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList$Builder;add(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;")
    )
    private ImmutableList.Builder<VoxelShape> academy$replaceStructureBounds(
            ImmutableList.Builder<VoxelShape> builder,
            Object broadPhaseShape,
            Operation<ImmutableList.Builder<VoxelShape>> original,
            Entity source,
            AABB bounds,
            @Local(ordinal = 1) Entity candidate,
            @Share("structureCollisions") LocalRef<CollisionQuery> sharedQuery
    ) {
        if (!(candidate instanceof BlockStructureCollision collision)) {
            return original.call(builder, broadPhaseShape);
        }
        var query = sharedQuery.get();
        if (query == null) {
            query = new CollisionQuery(bounds);
            sharedQuery.set(query);
        }
        var shapes = query.collect(collision);
        // Call the wrapped operation exactly once, including for an ignored structure,
        // so another mixin's additions are neither bypassed nor repeated per block.
        var result = original.call(builder, shapes.isEmpty() ? Shapes.empty() : shapes.getFirst());
        for (int index = 1; index < shapes.size(); index++) result.add(shapes.get(index));
        return result;
    }

    @ModifyReturnValue(method = "getEntityCollisions", at = @At("RETURN"))
    private List<VoxelShape> academy$appendIndexedStructures(
            List<VoxelShape> original,
            Entity source,
            AABB bounds,
            @Share("structureCollisions") LocalRef<CollisionQuery> sharedQuery
    ) {
        if (bounds.getSize() < 1.0e-7 || !((Object) this instanceof Level level)) return original;
        var indexedStructures = BlockStructureCollisionRuntime.query(level, bounds.inflate(1.0e-7));
        if (indexedStructures.isEmpty()) return original;
        Predicate<Entity> predicate = source == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
        var query = sharedQuery.get();
        if (query == null) {
            query = new CollisionQuery(bounds);
            sharedQuery.set(query);
        }
        return query.appendTo(original, indexedStructures.stream()
                .filter(entity -> entity != source && predicate.test(entity))
                .map(entity -> (BlockStructureCollision) entity)
                .toList());
    }
}
