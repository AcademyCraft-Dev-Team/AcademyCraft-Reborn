package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

/**
 * Placement context whose level is the shared plot. Directions, yaw and the nearest-look order are
 * expressed in the structure's local frame, so a rotated structure places stairs, furnaces and
 * levers the same way the actor sees them.
 */
final class SubLevelPlaceContext extends BlockPlaceContext {
    private final Vec3 localLook;
    private final float localRotation;
    private final Direction[] localNearest;

    SubLevelPlaceContext(Level plotLevel, Player player, InteractionHand hand, ItemStack stack,
                         BlockHitResult plotHit, StructurePose pose, BlockPos plotOrigin) {
        super(plotLevel, player, hand, stack, plotHit);
        this.localLook = pose.worldToLocalDirection(player.getLookAngle());
        double horizontalX = localLook.x;
        double horizontalZ = localLook.z;
        if (horizontalX * horizontalX + horizontalZ * horizontalZ < 1.0E-12) {
            var horizontal = pose.worldToLocalDirection(new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z));
            horizontalX = horizontal.x;
            horizontalZ = horizontal.z;
        }
        this.localRotation = horizontalX * horizontalX + horizontalZ * horizontalZ < 1.0E-12
                ? 0.0F : (float) Math.toDegrees(Math.atan2(-horizontalX, horizontalZ));
        this.localNearest = nearest(localLook);
    }

    @Override
    public Direction getHorizontalDirection() {
        return Direction.fromYRot(localRotation);
    }

    @Override
    public float getRotation() {
        return localRotation;
    }

    @Override
    public Direction getNearestLookingDirection() {
        return localNearest[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return localLook.y > 0 ? Direction.UP : Direction.DOWN;
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        var directions = localNearest.clone();
        if (replacingClickedOnBlock()) return directions;
        var clickedFace = getClickedFace();
        int index = 0;
        while (index < directions.length && directions[index] != clickedFace.getOpposite()) index++;
        if (index > 0) {
            System.arraycopy(directions, 0, directions, 1, index);
            directions[0] = clickedFace.getOpposite();
        }
        return directions;
    }

    @Override
    public boolean isSecondaryUseActive() {
        return getPlayer() != null && getPlayer().isSecondaryUseActive();
    }

    private static Direction[] nearest(Vec3 look) {
        double x = Math.abs(look.x), y = Math.abs(look.y), z = Math.abs(look.z);
        var dx = look.x > 0 ? Direction.EAST : Direction.WEST;
        var dy = look.y > 0 ? Direction.UP : Direction.DOWN;
        var dz = look.z > 0 ? Direction.SOUTH : Direction.NORTH;
        if (x > z) {
            if (y > x) return ordered(dy, dx, dz);
            return z > y ? ordered(dx, dz, dy) : ordered(dx, dy, dz);
        }
        if (y > z) return ordered(dy, dz, dx);
        return x > y ? ordered(dz, dx, dy) : ordered(dz, dy, dx);
    }

    private static Direction[] ordered(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }
}
