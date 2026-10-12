package org.academy.internal.common.structure.space;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

/** Direction ordering in the structure's local gravity frame, without moving or rotating an actor. */
final class NativeStructurePlacementDirections {
    private final Vec3 look;
    private final float rotation;

    NativeStructurePlacementDirections(StructurePose pose, Vec3 worldLook, Vec3 worldHorizontalLook) {
        look = pose.worldToLocalDirection(worldLook);
        var horizontal = look;
        if (horizontal.x * horizontal.x + horizontal.z * horizontal.z < 1.0e-12) {
            horizontal = pose.worldToLocalDirection(worldHorizontalLook);
        }
        rotation = horizontal.x * horizontal.x + horizontal.z * horizontal.z < 1.0e-12
                ? 0.0F : (float) Math.toDegrees(Math.atan2(-horizontal.x, horizontal.z));
    }

    float rotation() { return rotation; }
    Direction horizontal() { return Direction.fromYRot(rotation); }
    Direction vertical() { return look.y > 0 ? Direction.UP : Direction.DOWN; }

    Direction[] nearest() {
        double x = Math.abs(look.x), y = Math.abs(look.y), z = Math.abs(look.z);
        var dx = look.x > 0 ? Direction.EAST : Direction.WEST;
        var dy = vertical();
        var dz = look.z > 0 ? Direction.SOUTH : Direction.NORTH;
        // Preserve the original nearest-axis tie ordering, expressed using local view components.
        if (x > z) {
            if (y > x) return ordered(dy, dx, dz);
            return z > y ? ordered(dx, dz, dy) : ordered(dx, dy, dz);
        }
        if (y > z) return ordered(dy, dz, dx);
        return x > y ? ordered(dz, dx, dy) : ordered(dz, dy, dx);
    }

    Direction[] placementOrder(Direction clickedFace, boolean replaceClicked) {
        var result = nearest();
        if (!replaceClicked) {
            var supportDirection = clickedFace.getOpposite();
            for (int index = 0; index < result.length; index++) {
                if (result[index] == supportDirection) {
                    System.arraycopy(result, 0, result, 1, index);
                    result[0] = supportDirection;
                    break;
                }
            }
        }
        return result;
    }

    private static Direction[] ordered(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }
}
