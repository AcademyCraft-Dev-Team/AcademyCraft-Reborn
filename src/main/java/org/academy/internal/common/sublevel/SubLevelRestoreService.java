package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.academy.mixin.common.PersistentEntitySectionManagerAccessor;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Transactional staticization of a plot-backed SubLevel back into host blocks. */
public final class SubLevelRestoreService {
    private SubLevelRestoreService() {
    }

    public static Result staticize(ServerLevel host, SubLevelRuntime runtime, SubLevelId id,
                                   @Nullable BlockPos target) {
        return staticize(host, runtime, id, target, null, 0.0);
    }

    /** Actor-aware staticization; every destination cell is range- and permission-checked. */
    public static Result staticize(ServerLevel host, SubLevelRuntime runtime, SubLevelId id,
                                   @Nullable BlockPos target, @Nullable ServerPlayer actor,
                                   double maximumDistance) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(id, "id");
        if (maximumDistance < 0.0 || !Double.isFinite(maximumDistance)) {
            throw new IllegalArgumentException("maximumDistance must be finite and non-negative");
        }
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Staticize requires the server thread");
        if (host != runtime.host()) return new Result(0, "host_dimension_mismatch");
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return new Result(0, "missing");
        var view = PlotView.capture(runtime.storage(), subLevel.plot(), subLevel.localBounds());
        if (view.cells().isEmpty()) return new Result(0, "empty");
        List<Entity> plotEntities;
        try {
            plotEntities = runtime.storage().entities(subLevel.plot());
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("SubLevel staticize could not load plot entities at {}", id, failure);
            return new Result(0, "entities_unavailable");
        }
        var base = target == null ? BlockPos.containing(subLevel.pose().worldOrigin()) : target;
        var planned = new ArrayList<Planned>(view.cells().size());
        for (var cell : view.cells()) {
            var world = new BlockPos(cell.position().getX() + base.getX(),
                    cell.position().getY() + base.getY(), cell.position().getZ() + base.getZ());
            if (!host.hasChunkAt(world) || !host.isInWorldBounds(world)) return new Result(0, "out_of_world");
            if (actor != null) {
                if (actor.level() != host) return new Result(0, "actor_dimension_mismatch");
                if (actor.position().distanceToSqr(Vec3.atCenterOf(world)) > maximumDistance * maximumDistance) {
                    return new Result(0, "target_out_of_range:" + world);
                }
                if (!host.mayInteract(actor, world)) return new Result(0, "permission_denied:" + world);
            }
            if (!host.getBlockState(world).isAir()) return new Result(0, "target_occupied:" + world);
            var entity = runtime.storage().blockEntity(subLevel.plot(), cell.position());
            planned.add(new Planned(world, cell.state(), entity == null ? null
                    : entity.saveWithFullMetadata(runtime.storage().level().registryAccess())));
        }
        var journal = SubLevelStaticizeJournal.journal(host.getServer()).orElse(null);
        if (journal == null) return new Result(0, "journal_unavailable");
        var targets = planned.stream().map(Planned::position).toList();
        StructureTransactionJournal.Record record;
        try {
            record = SubLevelStaticizeJournal.begin(journal, host, base, id, targets);
        } catch (IOException failure) {
            AcademyCraft.LOGGER.error("SubLevel staticize journal write failed at {}", id, failure);
            return new Result(0, "journal_write_failed");
        }
        var placed = new ArrayList<BlockPos>(planned.size());
        try {
            for (var entry : planned) {
                if (!host.setBlock(entry.position(), entry.state(), SubLevelCaptureService.TRANSACTION_FLAGS)) {
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
        } catch (RuntimeException failure) {
            var rolledBack = rollback(host, placed);
            if (rolledBack) SubLevelStaticizeJournal.finish(journal, record.transactionId());
            AcademyCraft.LOGGER.warn("SubLevel staticize rolled back at {} (journal kept={})",
                    id, !rolledBack, failure);
            return new Result(0, rolledBack ? "rollback:" + failure.getMessage() : "recovery_required:" + failure.getMessage());
        }
        var moved = new ArrayList<Moved>();
        if (!migrateEntities(plotEntities, runtime.storage(), subLevel.plot(), host, base, moved)) {
            boolean blocksRestored = rollback(host, placed);
            boolean entitiesRestored = reverseEntities(moved);
            if (blocksRestored && entitiesRestored) SubLevelStaticizeJournal.finish(journal, record.transactionId());
            return new Result(0, "entities_migration_failed");
        }
        try {
            SubLevelStaticizeJournal.commit(journal, record);
        } catch (IOException failure) {
            var rolledBack = rollback(host, placed);
            boolean entitiesRestored = reverseEntities(moved);
            if (rolledBack && entitiesRestored) SubLevelStaticizeJournal.finish(journal, record.transactionId());
            return new Result(0, rolledBack ? "journal_commit_failed" : "recovery_required:journal_commit_failed");
        }
        try {
            runtime.remove(id);
            runtime.persist();
        } catch (RuntimeException failure) {
            // The blocks are committed; startup recovery will finish removing the SubLevel.
            AcademyCraft.LOGGER.error("SubLevel staticize committed but remove failed at {}", id, failure);
            return new Result(planned.size(), "committed_pending_recovery");
        }
        SubLevelStaticizeJournal.finish(journal, record.transactionId());
        return new Result(planned.size(), "staticized");
    }

    private static boolean rollback(ServerLevel host, List<BlockPos> placed) {
        boolean complete = true;
        for (var position : placed) {
            try {
                if (!host.getBlockState(position).isAir()
                        && !host.setBlock(position, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS)) {
                    complete = false;
                }
            } catch (RuntimeException failure) {
                complete = false;
                AcademyCraft.LOGGER.error("SubLevel staticize rollback failed at {}", position, failure);
            }
        }
        return complete;
    }

    private static boolean migrateEntities(List<Entity> entities, PlotStorage storage, PlotPos plot,
                                            ServerLevel host, BlockPos base, List<Moved> moved) {
        var layout = storage.layout();
        double originX = layout.chunkMinX(plot) * 16.0;
        double originZ = layout.chunkMinZ(plot) * 16.0;
        for (var entity : entities) {
            if (entity.isRemoved()) continue;
            if (entity instanceof ServerPlayer) return false;
            var oldPosition = entity.position();
            double targetX = base.getX() + (oldPosition.x - originX);
            double targetY = base.getY() + oldPosition.y;
            double targetZ = base.getZ() + (oldPosition.z - originZ);
            float yaw = entity.getYRot(), pitch = entity.getXRot();
            try {
                // The destination section can still be hidden this tick. Use teleport's actual
                // returned instance; ServerLevel.getEntity(UUID) only searches visible entities.
                var migrated = entity.teleport(new TeleportTransition(host,
                        new Vec3(targetX, targetY, targetZ), Vec3.ZERO, yaw, pitch, Set.of(),
                        TeleportTransition.DO_NOTHING));
                if (migrated == null) return false;
                moved.add(new Moved(migrated, storage.level(), oldPosition, yaw, pitch));
                // NeoForge can veto EntityJoinLevelEvent even though vanilla teleport returns
                // a new instance. Keep it in the rollback list, but never commit an unadded entity.
                if (!registered(host, migrated)) return false;
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("SubLevel staticize entity migration failed for {}", entity.getUUID(), failure);
                return false;
            }
        }
        return true;
    }

    private static boolean reverseEntities(List<Moved> moved) {
        boolean complete = true;
        for (int index = moved.size() - 1; index >= 0; index--) {
            var entry = moved.get(index);
            try {
                var restored = entry.entity().teleport(new TeleportTransition(entry.level(), entry.position(),
                        Vec3.ZERO, entry.yaw(), entry.pitch(), Set.of(), TeleportTransition.DO_NOTHING));
                if (restored == null || !registered(entry.level(), restored)) complete = false;
            } catch (RuntimeException failure) {
                complete = false;
                AcademyCraft.LOGGER.error("SubLevel staticize could not move entity {} back",
                        entry.entity().getUUID(), failure);
            }
        }
        return complete;
    }

    private record Moved(Entity entity, ServerLevel level, Vec3 position, float yaw, float pitch) {
    }

    @SuppressWarnings("unchecked")
    private static boolean registered(ServerLevel level, Entity entity) {
        var manager = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
        var access = (PersistentEntitySectionManagerAccessor<Entity>) (Object) manager;
        var section = access.academy$getSectionStorage().getSection(SectionPos.asLong(entity.blockPosition()));
        return section != null && section.getEntities().anyMatch(candidate -> candidate == entity);
    }

    public record Result(int blocks, String detail) {
        public boolean succeeded() {
            return blocks > 0;
        }
    }

    private record Planned(BlockPos position, BlockState state, net.minecraft.nbt.CompoundTag tag) {
    }
}
