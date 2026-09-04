package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Entry point for creating movable structures from server-world blocks. */
public final class BlockStructureApi {
    private BlockStructureApi() {
    }

    public static BlockStructureCaptureResult capture(
            ServerLevel level,
            Iterable<BlockPos> positions
    ) {
        return capture(level, positions, BlockStructureCaptureOptions.defaults());
    }

    public static BlockStructureCaptureResult capture(
            ServerLevel level,
            Iterable<BlockPos> positions,
            BlockStructureCaptureOptions options
    ) {
        return BlockStructureManager.capture(level, positions, options);
    }

    /** Captures the six-directionally connected component containing {@code seed}. */
    public static BlockStructureCaptureResult captureConnected(
            ServerLevel level,
            BlockPos seed,
            BlockStructureCaptureOptions options
    ) {
        return BlockStructureManager.captureConnected(level, seed, options);
    }

    /** Finds a connected movable component without modifying the world. */
    public static BlockStructureSelectionResult selectConnected(
            ServerLevel level,
            BlockPos seed,
            int maximumBlocks,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectConnected(
                level, seed, maximumBlocks, policy);
    }

    /** Finds every capturable block whose grid position is inside a spherical volume. */
    public static BlockStructureSelectionResult selectSphere(
            ServerLevel level,
            BlockPos center,
            double radius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectSphere(level, center, radius, policy);
    }

    /** Finds every capturable block whose grid position is inside an axis-aligned ellipsoid. */
    public static BlockStructureSelectionResult selectEllipsoid(
            ServerLevel level,
            BlockPos center,
            double horizontalRadius,
            double verticalRadius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return selectEllipsoid(
                level,
                center,
                horizontalRadius,
                verticalRadius,
                horizontalRadius,
                policy
        );
    }

    /** Finds capturable blocks inside an axis-aligned ellipsoid with independent axes. */
    public static BlockStructureSelectionResult selectEllipsoid(
            ServerLevel level,
            BlockPos center,
            double xRadius,
            double yRadius,
            double zRadius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectEllipsoid(
                level, center, xRadius, yRadius, zRadius, policy);
    }

    /**
     * Finds capturable blocks in the union of a flattened ellipsoid and a centered cube.
     * The cube preserves compact buildings whose corners fall outside the ellipsoid. A cube
     * radius of one represents a 3x3x3 core.
     */
    public static BlockStructureSelectionResult selectEllipsoidWithCubeCore(
            ServerLevel level,
            BlockPos center,
            double horizontalRadius,
            double verticalRadius,
            int cubeRadius,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectEllipsoidWithCubeCore(
                level,
                center,
                horizontalRadius,
                verticalRadius,
                horizontalRadius,
                cubeRadius,
                policy
        );
    }

    /**
     * Finds capturable blocks in a lower half-ellipsoid beneath {@code center} and an upper
     * circular cylinder above it. The center layer belongs to the terrain half; a cylinder
     * height of five includes exactly the five layers at offsets {@code +1} through {@code +5}.
     * This shape can lift a broad natural foundation together with a small building above it.
     */
    public static BlockStructureSelectionResult selectLowerEllipsoidWithUpperCylinder(
            ServerLevel level,
            BlockPos center,
            double horizontalRadius,
            double verticalRadius,
            double cylinderRadius,
            int cylinderHeight,
            BlockStructureCaptureOptions.CapturePolicy policy
    ) {
        return BlockStructureManager.selectLowerEllipsoidWithUpperCylinder(
                level,
                center,
                horizontalRadius,
                verticalRadius,
                horizontalRadius,
                cylinderRadius,
                cylinderHeight,
                policy
        );
    }

    /**
     * Resolves the first visible structure under an entity's view ray. World blocks occlude
     * the query, making this suitable for skills and precision-operation controls.
     */
    public static Optional<BlockStructure> findLookedAt(
            Entity viewer,
            double maximumRange,
            Predicate<BlockStructure> filter
    ) {
        if (viewer == null || filter == null || !Double.isFinite(maximumRange)
                || maximumRange <= 0.0 || maximumRange > 256.0) {
            return Optional.empty();
        }
        var start = viewer.getEyePosition();
        var containingStructure = viewer.level().getEntities(
                        viewer,
                        viewer.getBoundingBox().expandTowards(start.subtract(viewer.position()))
                                .inflate(1.0e-4),
                        candidate -> candidate instanceof BlockStructure structure
                                && candidate.isAlive()
                                && candidate.isPickable()
                                && filter.test(structure)
                                && viewerInsideStructureBounds(
                                candidate.getBoundingBox(), viewer.getBoundingBox(),
                                viewer.position(), start))
                .stream()
                .min(java.util.Comparator.comparingDouble(candidate ->
                        candidate.getBoundingBox().getCenter().distanceToSqr(start)));
        if (containingStructure.isPresent()
                && containingStructure.get() instanceof BlockStructure structure) {
            return Optional.of(structure);
        }
        var direction = viewer.getLookAngle();
        if (direction.lengthSqr() <= 1.0e-8) return Optional.empty();
        var end = start.add(direction.scale(maximumRange));
        var blockHit = viewer.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                viewer
        ));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                viewer.level(),
                viewer,
                start,
                end,
                viewer.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0),
                candidate -> candidate instanceof BlockStructure structure
                        && candidate.isAlive()
                        && candidate.isPickable()
                        && filter.test(structure),
                0.3f
        );
        return hit != null && hit.getEntity() instanceof BlockStructure structure
                ? Optional.of(structure)
                : Optional.empty();
    }

    /** Returns whether any part of the viewer occupies a structure's outer AABB. */
    public static boolean isViewerInside(Entity viewer, BlockStructure structure) {
        return viewer != null && structure != null
                && viewer.level() == structure.asEntity().level()
                && viewerInsideStructureBounds(
                structure.asEntity().getBoundingBox(),
                viewer.getBoundingBox(),
                viewer.position(),
                viewer.getEyePosition()
        );
    }

    static boolean viewerInsideStructureBounds(
            AABB structureBounds,
            AABB viewerBounds,
            Vec3 position,
            Vec3 eyePosition
    ) {
        return structureBounds != null && viewerBounds != null
                && position != null && eyePosition != null
                && (structureBounds.contains(position)
                || structureBounds.contains(eyePosition)
                || structureBounds.intersects(viewerBounds));
    }

    /**
     * Recursively removes candidates that would immediately meet an external block while moving
     * one grid step in {@code movementDirection}. The world is not modified.
     */
    public static List<BlockPos> cropImmediatelyBlocked(
            ServerLevel level,
            Iterable<BlockPos> candidates,
            Direction movementDirection
    ) {
        return BlockStructureManager.cropImmediatelyBlocked(
                level, candidates, movementDirection);
    }

    /**
     * Keeps positions whose block centers lie at least the requested distance in front of a
     * plane origin. This prevents captured structures from enclosing their controller.
     */
    public static List<BlockPos> cropToForwardHalfSpace(
            Iterable<BlockPos> candidates,
            Vec3 origin,
            Vec3 forward,
            double minimumForwardDistance
    ) {
        return BlockStructureManager.cropToForwardHalfSpace(
                candidates, origin, forward, minimumForwardDistance);
    }

    /**
     * Generates a structure entity from existing snapshot data without changing
     * world blocks. The legacy restore flag is retained for data compatibility;
     * every stopped structure now settles to blocks or drops.
     */
    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position
    ) {
        return spawn(level, snapshot, position, true, false);
    }

    /** Generates a structure entity from existing snapshot data without changing world blocks. */
    public static Optional<BlockStructure> spawn(
            ServerLevel level,
            BlockStructureSnapshot snapshot,
            Vec3 position,
            boolean gravityEnabled,
            boolean restoreWhenSettled
    ) {
        return BlockStructureManager.spawn(
                level, snapshot, position, gravityEnabled, restoreWhenSettled);
    }
}
