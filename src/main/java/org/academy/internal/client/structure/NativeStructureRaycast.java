package org.academy.internal.client.structure;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.Optional;
import java.util.UUID;

/** Exact selection against state-only local shapes; never replaces a vanilla world-coordinate hit. */
public final class NativeStructureRaycast {
    private static final double MAX_REACH = 128;

    private NativeStructureRaycast() {
    }

    /** Returns a projection only when it is closer than both the ordinary world and entity hit. */
    public static Optional<Hit> pick(Entity camera, double reach, float partialTick) {
        if (!(camera.level() instanceof ClientLevel level) || !Double.isFinite(reach)
                || reach <= 0 || reach > MAX_REACH) return Optional.empty();
        var start = camera.getEyePosition(partialTick);
        var end = start.add(camera.getViewVector(partialTick).scale(reach));
        var nearest = reach * reach;
        var worldHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        if (worldHit.getType() != HitResult.Type.MISS) nearest = start.distanceToSqr(worldHit.getLocation());
        var search = new AABB(start, end).inflate(1);
        var entityHit = ProjectileUtil.getEntityHitResult(camera, start, end, search,
                entity -> !(entity instanceof NativeStructureProjectionEntity)
                        && !entity.isSpectator() && EntitySelector.CAN_BE_PICKED.test(entity), nearest);
        if (entityHit != null) nearest = Math.min(nearest, start.distanceToSqr(entityHit.getLocation()));
        Hit result = null;
        // Entity section lookup is origin-based and can miss a rotated view extending far from its origin.
        // Only tracked projections are inspected; each cheap transformed-bounds rejection precedes DDA.
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof NativeStructureProjectionEntity projection) || projection.isRemoved()
                    || !projection.getBoundingBox().intersects(search)) continue;
            var hit = raycast(projection, start, end, partialTick);
            if (hit.isPresent() && hit.get().distanceSquared() + 1.0e-8 < nearest) {
                result = hit.get();
                nearest = result.distanceSquared();
            }
        }
        return Optional.ofNullable(result);
    }

    /** A single-view geometric query. Call pick when ordinary world/entity occlusion matters. */
    public static Optional<Hit> raycast(NativeStructureProjectionEntity projection,
                                      Vec3 worldStart, Vec3 worldEnd, float partialTick) {
        if (projection.isRemoved() || projection.view().isEmpty() || !finite(worldStart) || !finite(worldEnd)
                || worldStart.distanceToSqr(worldEnd) > MAX_REACH * MAX_REACH) return Optional.empty();
        var pose = projection.renderPose(partialTick);
        var localStart = pose.worldToLocalPoint(worldStart);
        var localEnd = pose.worldToLocalPoint(worldEnd);
        var localHit = projection.view().clip(new ClipContext(localStart, localEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (localHit.getType() == HitResult.Type.MISS) return Optional.empty();
        var worldPoint = pose.localToWorldPoint(localHit.getLocation());
        var face = localHit.getDirection();
        var normal = pose.localToWorldDirection(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
        var state = projection.viewState();
        return Optional.of(new Hit(projection, state.reference(), state.runtimeId(), state.revision(), pose,
                localHit.getBlockPos(), face, localHit.getLocation(), worldPoint, normal,
                worldStart.distanceToSqr(worldPoint), localHit.isInside()));
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    public record Hit(
            NativeStructureProjectionEntity projection, StructureRef reference, UUID runtimeId, long revision,
            StructurePose pose, BlockPos localPosition, Direction localFace, Vec3 localLocation,
            Vec3 worldLocation, Vec3 worldNormal, double distanceSquared, boolean inside
    ) {
        public Hit {
            localPosition = localPosition.immutable();
        }

        public StructureHit structureHit() {
            return new StructureHit(new SpaceBlockRef(reference, localPosition), runtimeId, revision,
                    pose, localFace, localLocation, worldLocation, inside);
        }
    }
}
