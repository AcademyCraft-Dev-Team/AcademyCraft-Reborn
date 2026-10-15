package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.structure.BlockStructureGridAlignment;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureStaticization;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared server-side safe-staticization preflight for the three structure node families. It
 * validates the structure epoch, the captured content and the complete target footprint
 * (loading, permission, occupation and rotatable block states) without writing anything; any
 * unmet precondition returns an explicit rejection code, and the content owner, its control
 * and the transaction journal stay untouched. The placement path is still unimplemented, so a
 * fully valid plan returns {@code unavailable} instead of a fake success.
 */
public final class StructureStaticizationRunner {
    private StructureStaticizationRunner() {
    }

    public static ProgramStructureOperationResult staticize(
            ServerPlayer player,
            ProgramStructureReference structure,
            @Nullable ProgramBlockPosition target,
            List<BlockPos> capturedCells
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(structure, "structure");
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramStructureOperationResult.unavailable("host_unavailable");
        }
        var handle = StructureSpaces.resolve(level.getServer(), structure.reference()).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return ProgramStructureOperationResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(structure.runtimeId())) {
            return ProgramStructureOperationResult.rejected("stale_structure_epoch");
        }
        if (!level.dimension().identifier().equals(structure.reference().dimension())) {
            return ProgramStructureOperationResult.rejected("structure_dimension_mismatch");
        }
        if (capturedCells == null || capturedCells.isEmpty()) {
            return ProgramStructureOperationResult.unavailable("structure_content_unavailable");
        }
        var quarterTurns = quarterTurns(handle);
        if (quarterTurns == null) {
            return ProgramStructureOperationResult.rejected("rotation_unsupported");
        }
        var origin = minimumCorner(capturedCells);
        var contents = new LinkedHashMap<BlockPos, StructureBlockData>();
        var cells = new ArrayList<StructureStaticization.LocalCell>(capturedCells.size());
        try {
            for (var position : capturedCells) {
                var local = position.subtract(origin);
                var content = handle.world().read(local);
                contents.put(local, content);
                cells.add(new StructureStaticization.LocalCell(
                        local, content.blockEntityData() != null));
            }
        } catch (IllegalArgumentException | IllegalStateException unavailableContent) {
            return ProgramStructureOperationResult.unavailable("structure_content_unavailable");
        }
        var anchor = anchor(level, handle.pose(), target);
        if (anchor == null) return ProgramStructureOperationResult.rejected("target_dimension_mismatch");
        var plan = StructureStaticization.plan(
                anchor,
                quarterTurns,
                true,
                cells,
                new LevelWorld(level, contents),
                position -> level.mayInteract(player, position));
        if (!plan.ready()) {
            var detail = plan.detail() + (plan.problemPosition() == null
                    ? "" : "@" + plan.problemPosition().toShortString());
            AcademyCraft.LOGGER.info(
                    "Safe staticization rejected for {} ({} cells, anchor {}): {}",
                    structure.reference(), cells.size(), anchor.toShortString(), detail);
            return ProgramStructureOperationResult.rejected(detail);
        }
        AcademyCraft.LOGGER.info(
                "Safe staticization preconditions hold for {} ({} cells, anchor {}); placement remains unavailable",
                structure.reference(), cells.size(), anchor.toShortString());
        return ProgramStructureOperationResult.unavailable("safe_staticization_unavailable");
    }

    private static @Nullable BlockPos anchor(
            ServerLevel level,
            StructurePose pose,
            @Nullable ProgramBlockPosition target
    ) {
        if (target == null) return BlockPos.containing(pose.worldOrigin());
        var dimension = level.dimension().identifier();
        if (!dimension.equals(target.dimension())) return null;
        return new BlockPos(target.x(), target.y(), target.z());
    }

    private static @Nullable Integer quarterTurns(StructureHandle handle) {
        var pose = handle.pose();
        var forward = pose.localToWorldDirection(new Vec3(1.0, 0.0, 0.0));
        var up = pose.localToWorldDirection(new Vec3(0.0, 1.0, 0.0));
        var side = pose.localToWorldDirection(new Vec3(0.0, 0.0, 1.0));
        if (up.y < 0.999) return null;
        if (forward.x > 0.5 && side.z > 0.5) return 0;
        if (forward.z > 0.5 && side.x < -0.5) return 1;
        if (forward.x < -0.5 && side.z < -0.5) return 2;
        if (forward.z < -0.5 && side.x > 0.5) return 3;
        return null;
    }

    private static BlockPos minimumCorner(List<BlockPos> positions) {
        var minimumX = Integer.MAX_VALUE;
        var minimumY = Integer.MAX_VALUE;
        var minimumZ = Integer.MAX_VALUE;
        for (var position : positions) {
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
        }
        return new BlockPos(minimumX, minimumY, minimumZ);
    }

    private record LevelWorld(
            ServerLevel level,
            Map<BlockPos, StructureBlockData> contents
    ) implements StructureStaticization.World {
        @Override
        public boolean isInWorldBounds(BlockPos position) {
            return level.isInWorldBounds(position);
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return level.hasChunkAt(position);
        }

        @Override
        public boolean isOccupied(BlockPos position) {
            if (!level.getBlockState(position).isAir()) return true;
            return !level.getEntities(null, new AABB(position)).isEmpty();
        }

        @Override
        public boolean canPlace(
                BlockPos position,
                StructureStaticization.LocalCell cell,
                int quarterTurns
        ) {
            var content = contents.get(cell.localPosition());
            if (content == null) return false;
            var rotated = content.state().rotate(
                    BlockStructureGridAlignment.rotation(quarterTurns));
            return !rotated.isAir() && rotated.canSurvive(level, position);
        }
    }
}
