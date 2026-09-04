package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/** Pure coordinate helpers for snapping a yaw-rotated structure to the block grid. */
public final class BlockStructureGridAlignment {
    private BlockStructureGridAlignment() {
    }

    public static Alignment nearest(
            BlockStructureSnapshot snapshot,
            Vec3 entityPosition,
            float yawDegrees
    ) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("Cannot align an empty structure");
        }
        return nearest(
                snapshot.width(),
                snapshot.depth(),
                snapshot.blocks().getFirst().relativePosition(),
                entityPosition,
                yawDegrees
        );
    }

    static Alignment nearest(
            int width,
            int depth,
            BlockPos anchorRelative,
            Vec3 entityPosition,
            float yawDegrees
    ) {
        if (width < 1 || depth < 1 || anchorRelative == null || entityPosition == null) {
            throw new IllegalArgumentException("Invalid structure alignment bounds");
        }
        var quarterTurns = nearestQuarterTurns(yawDegrees);
        var rotatedAnchor = rotateAroundPivot(
                anchorRelative.getX(),
                anchorRelative.getZ(),
                width * 0.5,
                depth * 0.5,
                quarterTurns
        );
        var anchorWorld = new BlockPos(
                nearestInteger(entityPosition.x + rotatedAnchor.x),
                nearestInteger(entityPosition.y + anchorRelative.getY()),
                nearestInteger(entityPosition.z + rotatedAnchor.z)
        );
        var alignedPosition = new Vec3(
                anchorWorld.getX() - rotatedAnchor.x,
                anchorWorld.getY() - anchorRelative.getY(),
                anchorWorld.getZ() - rotatedAnchor.z
        );
        return new Alignment(
                quarterTurns,
                rotation(quarterTurns),
                anchorRelative,
                anchorWorld,
                alignedPosition
        );
    }

    public static int nearestQuarterTurns(float yawDegrees) {
        if (!Float.isFinite(yawDegrees)) return 0;
        return Math.floorMod(Mth.floor(yawDegrees / 90.0f + 0.5f), 4);
    }

    public static BlockPos rotateOffset(BlockPos offset, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case 2 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case 3 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset.immutable();
        };
    }

    public static Rotation rotation(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static RotatedPoint rotateAroundPivot(
            double x,
            double z,
            double pivotX,
            double pivotZ,
            int quarterTurns
    ) {
        var offsetX = x - pivotX;
        var offsetZ = z - pivotZ;
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new RotatedPoint(pivotX - offsetZ, pivotZ + offsetX);
            case 2 -> new RotatedPoint(pivotX - offsetX, pivotZ - offsetZ);
            case 3 -> new RotatedPoint(pivotX + offsetZ, pivotZ - offsetX);
            default -> new RotatedPoint(x, z);
        };
    }

    private static int nearestInteger(double value) {
        return Mth.floor(value + 0.5);
    }

    public record Alignment(
            int quarterTurns,
            Rotation rotation,
            BlockPos anchorRelative,
            BlockPos anchorWorld,
            Vec3 entityPosition
    ) {
        public BlockPos target(BlockPos relativePosition) {
            return anchorWorld.offset(rotateOffset(
                    relativePosition.subtract(anchorRelative),
                    quarterTurns
            ));
        }

        public float yawDegrees() {
            return quarterTurns * 90.0f;
        }
    }

    private record RotatedPoint(double x, double z) {
    }
}
