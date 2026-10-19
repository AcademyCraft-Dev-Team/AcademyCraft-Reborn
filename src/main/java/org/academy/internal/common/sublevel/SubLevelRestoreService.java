package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transactional staticization of a plot-backed SubLevel back into host blocks. */
public final class SubLevelRestoreService {
    private SubLevelRestoreService() {
    }

    public static Result staticize(ServerLevel host, SubLevelRuntime runtime, SubLevelId id,
                                   @Nullable BlockPos target) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(id, "id");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Staticize requires the server thread");
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return new Result(0, "missing");
        var view = runtime.view(id);
        if (view.cells().isEmpty()) return new Result(0, "empty");
        var base = target == null ? BlockPos.containing(subLevel.pose().worldOrigin()) : target;
        var planned = new ArrayList<Planned>(view.cells().size());
        for (var cell : view.cells()) {
            var world = new BlockPos(cell.position().getX() + base.getX(),
                    cell.position().getY() + base.getY(), cell.position().getZ() + base.getZ());
            if (!host.hasChunkAt(world) || !host.isInWorldBounds(world)) return new Result(0, "out_of_world");
            if (!host.getBlockState(world).isAir()) return new Result(0, "target_occupied:" + world);
            var entity = runtime.storage().blockEntity(subLevel.plot(), cell.position());
            planned.add(new Planned(world, cell.state(), entity == null ? null
                    : entity.saveWithFullMetadata(runtime.storage().level().registryAccess())));
        }
        var placed = new ArrayList<BlockPos>(planned.size());
        try {
            for (var entry : planned) {
                if (!host.setBlock(entry.position(), entry.state(), 3)) {
                    throw new IllegalStateException("Could not place staticized block " + entry.position());
                }
                placed.add(entry.position());
                if (entry.tag() != null) {
                    var loaded = BlockEntity.loadStatic(entry.position(), entry.state(), entry.tag(),
                            host.registryAccess());
                    if (loaded == null) throw new IllegalStateException("Could not restore block entity");
                    host.setBlockEntity(loaded);
                    loaded.setChanged();
                }
            }
            runtime.remove(id);
            return new Result(planned.size(), "staticized");
        } catch (RuntimeException failure) {
            for (var position : placed) host.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            AcademyCraft.LOGGER.warn("SubLevel staticize rolled back at {}", id, failure);
            return new Result(0, "rollback:" + failure.getMessage());
        }
    }

    public record Result(int blocks, String detail) {
        public boolean succeeded() {
            return blocks > 0;
        }
    }

    private record Planned(BlockPos position, BlockState state, net.minecraft.nbt.CompoundTag tag) {
    }
}
