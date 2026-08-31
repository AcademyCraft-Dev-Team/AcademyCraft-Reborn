package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.stream.Stream;

/** Shared collision-safe destination helpers for group-control adapters and skills. */
public final class GroupControlNavigation {
    private static final int[][] SEARCH_OFFSETS = {
            {0, 0, 0}, {0, 1, 0}, {0, 2, 0}, {0, -1, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}
    };

    private GroupControlNavigation() {
    }

    /**
     * Finds a nearby loaded position that can contain the subject's current collision box.
     * Vertical candidates are deliberately preferred so a clicked solid block resolves to
     * the open space immediately above it instead of to an unrelated horizontal cell.
     */
    public static Optional<Vec3> findNearestOccupablePosition(
            LivingEntity subject,
            Vec3 preferred
    ) {
        if (subject == null || preferred == null || !isFinite(preferred)) return Optional.empty();
        for (var offset : SEARCH_OFFSETS) {
            var candidate = preferred.add(offset[0], offset[1], offset[2]);
            if (canOccupy(subject, candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Finds a collision-free position from which the subject can reach a work block. */
    public static Optional<Vec3> findNearestWorkPosition(
            LivingEntity subject,
            BlockPos workBlock
    ) {
        if (subject == null || workBlock == null) return Optional.empty();
        return Stream.of(
                        workBlock.above(),
                        workBlock.north(), workBlock.south(), workBlock.west(), workBlock.east(),
                        workBlock.north().west(), workBlock.north().east(),
                        workBlock.south().west(), workBlock.south().east(),
                        workBlock.north().above(), workBlock.south().above(),
                        workBlock.west().above(), workBlock.east().above(),
                        workBlock.above(2)
                )
                .map(BlockPos::immutable)
                .map(Vec3::atBottomCenterOf)
                .filter(candidate -> canOccupy(subject, candidate))
                .min(java.util.Comparator.comparingDouble(subject::distanceToSqr));
    }

    /** Checks the loaded world, border, and the subject's full collision box. */
    public static boolean canOccupy(LivingEntity subject, Vec3 candidate) {
        if (subject == null || candidate == null || !isFinite(candidate)) return false;
        var level = subject.level();
        var block = BlockPos.containing(candidate);
        if (block.getY() < level.getMinY() || block.getY() >= level.getMaxY()
                || !level.hasChunkAt(block)) return false;
        var moved = subject.getBoundingBox().move(candidate.subtract(subject.position()));
        return level.getWorldBorder().isWithinBounds(moved)
                && level.noCollision(subject, moved);
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
