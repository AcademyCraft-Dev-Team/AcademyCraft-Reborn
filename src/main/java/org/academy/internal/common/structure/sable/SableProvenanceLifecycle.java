package org.academy.internal.common.structure.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.compat.SableContainerMixinGate;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Pinned backend lifecycle evidence, never proximity inference or polling splitFrom after it was cleared. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SableProvenanceLifecycle {
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private SableProvenanceLifecycle() {}

    public static StructureRef reference(ServerSubLevel ship) {
        return new StructureRef(ship.getUniqueId(), ship.getLevel().dimension().identifier(), StructureBackendRouter.SABLE_ID, 0);
    }

    /** Scope survives an emptied parent: Sable omits setSplitFrom when moving its last cell. */
    public static Object assemble(net.minecraft.server.level.ServerLevel host, net.minecraft.core.BlockPos anchor,
                                  java.util.function.Supplier<Object> assembly) {
        SableStructureAccess.requireThread(host.getServer());
        var grid = SubLevelContainer.getContainer(host);
        var plot = grid == null ? null : grid.getPlot(net.minecraft.world.level.ChunkPos.containing(anchor));
        var parent = plot == null ? null : org.academy.internal.common.structure.sable.reflect.SableReflection
                .optional(ServerSubLevel.class, plot.getSubLevel());
        var source = parent == null || parent.isRemoved() || parent.getLevel() != host
                || grid.getSubLevel(parent.getUniqueId()) != parent || SableRestorationIsolation.hidden(parent)
                ? null : reference(parent);
        var metadata = source == null ? null : LiveStructureProvenance.metadata(host.getServer(), source).orElse(null);
        var mounts = SableNozzleFractures.capture(host, parent, source);
        var result = assembly.get();
        var child = org.academy.internal.common.structure.sable.reflect.SableReflection.optional(ServerSubLevel.class, result);
        if (source != null && child != null && child != parent && child.getLevel() == host && !child.isRemoved()
                && grid.getSubLevel(child.getUniqueId()) == child && !SableRestorationIsolation.hidden(child)) {
            org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.fragmentCreated(host, source, reference(child));
            SableNozzleFractures.moved(host, parent, child, mounts);
        }
        if (metadata == null) return result;
        if (child == null || child == parent || child.getLevel() != host || child.isRemoved()
                || grid.getSubLevel(child.getUniqueId()) != child || SableRestorationIsolation.hidden(child)
                || !LiveStructureProvenance.metadata(host.getServer(), source).filter(metadata::equals).isPresent()) return result;
        // The parent was an exact live object at entry. It may have become empty/removed
        // inside this synchronous assembly, but no unavailable historical source is inferred.
        if (!LiveStructureProvenance.commitFragment(host.getServer(), reference(child), source, host.getGameTime())) {
            AcademyCraft.LOGGER.error("Sable split provenance was not published for child {} from {}", child.getUniqueId(), parent.getUniqueId());
        }
        return result;
    }

    /** Before backend mutation: REMOVED is an explicit permanent-deletion decision, UNLOADED is not. */
    public static void removing(ServerSubLevelContainer grid, int x, int z, SubLevelRemovalReason reason) {
        if (reason != SubLevelRemovalReason.REMOVED) return;
        var ship = grid.getSubLevel(x, z);
        if (!(ship instanceof ServerSubLevel source)) return;
        var server = source.getLevel().getServer();
        SableStructureAccess.requireThread(server);
        var reference = reference(source);
        // The restoration journal already owns its exact-source retirement and recovery.
        if (MarkedGridRestoration.pending(server, reference)) return;
        var metadata = LiveStructureProvenance.metadata(server, reference).orElse(null);
        if (metadata == null) return;
        var state = state(server);
        if (state.journal == null || state.journal.contains(reference)) return;
        var evidence = new CompoundTag();
        evidence.putInt("plot_x", x); evidence.putInt("plot_z", z);
        var pointer = source.getLastSerializationPointer();
        evidence.putBoolean("saved", pointer != null);
        if (pointer != null) {
            evidence.putInt("chunk_x", pointer.chunkPos().x()); evidence.putInt("chunk_z", pointer.chunkPos().z());
            evidence.putInt("storage", pointer.storageIndex()); evidence.putInt("slot", pointer.subLevelIndex());
        }
        try { state.journal.prepare(new SableProvenanceRetirements.Entry(reference, metadata, evidence)); }
        catch (IOException | RuntimeException failure) {
            // Never throw halfway through Sable's split/removal sweep. Retain the original mark on failure.
            state.progress.record(reference, SableRetirementStatus.INTENT_WRITE_FAILED, server.getTickCount(), false);
            AcademyCraft.LOGGER.error("Sable permanent deletion provenance could not be journaled: {}", reference, failure);
        }
    }

    public static boolean hasPending(MinecraftServer server) {
        var state = state(server);
        return state.journal == null || !state.journal.healthy() || !state.journal.entries().isEmpty();
    }

    public static boolean pending(MinecraftServer server, StructureRef reference) {
        var state = state(server);
        if (state.journal == null || !state.journal.healthy()) {
            return LiveStructureProvenance.metadata(server, reference).isPresent();
        }
        return state.journal.contains(reference);
    }

    public record Observation(StructureRef reference, SableRetirementStatus status, int attempts,
                              long lastTick, long nextTick) {}
    public record Diagnostics(String backend, String state, int pendingCount, int problemCount,
                              java.util.List<SableProvenanceRetirements.Problem> problems,
                              java.util.List<Observation> pending, java.util.List<Observation> recent) {
        public Diagnostics {
            problems = java.util.List.copyOf(problems);
            pending = java.util.List.copyOf(pending);
            recent = java.util.List.copyOf(recent);
        }
    }

    /** Bounded read of loaded observations. Does not open world files, initialize a ledger or retry work. */
    public static Diagnostics diagnostics(MinecraftServer server, @org.jspecify.annotations.Nullable StructureRef target) {
        SableStructureAccess.requireThread(server);
        var backend = SableContainerMixinGate.state();
        var state = SERVERS.get(server);
        if (backend != SableContainerMixinGate.State.VERIFIED || state == null) {
            return new Diagnostics(backend.name(), backend == SableContainerMixinGate.State.VERIFIED
                    ? "not_initialized" : "backend_unavailable", 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
        var journal = state.journal;
        var records = new java.util.ArrayList<Observation>();
        if (journal != null) {
            if (target != null) journal.entry(target).ifPresent(entry -> records.add(observation(state.progress.get(entry.reference()))));
            else for (var entry : journal.entries()) {
                records.add(observation(state.progress.get(entry.reference())));
                if (records.size() == 10) break;
            }
        }
        var recent = state.progress.recent().stream().filter(attempt -> target == null || attempt.reference().equals(target))
                .map(SableProvenanceLifecycle::observation).toList();
        return new Diagnostics(backend.name(), journal == null ? "journal_unavailable"
                : !journal.healthy() ? "journal_unhealthy" : !state.ledgerObserved ? "creation_ledger_unobserved"
                : !state.ledgerAvailable ? "creation_ledger_unavailable" : "ready",
                journal == null ? 0 : journal.size(), journal == null ? 0 : journal.problemCount(),
                journal == null ? java.util.List.of() : journal.problems(), records, recent);
    }

    private static Observation observation(SableRetirementProgress.Attempt attempt) {
        return new Observation(attempt.reference(), attempt.status(), attempt.attempts(), attempt.lastTick(), attempt.nextTick());
    }

    /** At most four records and one storage retirement per 20 ticks; never loads a world chunk. */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (SableContainerMixinGate.state() != SableContainerMixinGate.State.VERIFIED) return;
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        var state = state(server);
        if (state.journal == null || !state.journal.healthy()) return;
        state.ledgerAvailable = LiveStructureProvenance.healthy(server);
        state.ledgerObserved = true;
        if (!state.ledgerAvailable) return;
        if (state.iterator == null || !state.iterator.hasNext()) state.iterator = state.journal.entries().iterator();
        for (int i = 0; i < 4 && state.iterator.hasNext(); i++) {
            var entry = state.iterator.next();
            var reference = entry.reference();
            if (!state.progress.due(reference, server.getTickCount())) continue;
            var current = LiveStructureProvenance.metadata(server, reference).orElse(null);
            if (current != null && !current.equals(entry.metadata())) {
                state.progress.record(reference, SableRetirementStatus.CREATION_CONFLICT, server.getTickCount(), true);
                continue;
            }
            var host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, reference.dimension()));
            var grid = host == null ? null : SubLevelContainer.getContainer(host);
            if (grid == null) {
                state.progress.record(reference, SableRetirementStatus.HOST_UNAVAILABLE, server.getTickCount(), true);
                continue;
            }
            var status = SableRetirementStatus.STORAGE_FAILURE;
            try {
                var evidence = entry.evidence();
                if (evidence.getBoolean("saved").orElseThrow()) {
                    status = new SableStructureTransactions(server).retireRemovedStatus(reference, evidence);
                } else {
                    // A never-saved child has no durable content to erase. An unexpected loaded
                    // activation is ambiguous (e.g. recovery before removal); do not guess.
                    status = SableRetirementStatus.UNSAVED_SOURCE_LIVE;
                    if (grid.getSubLevel(reference.id()) == null) {
                        status = SableRetirementStatus.STORAGE_FAILURE;
                        SableRestorationDurability.occupancy(host);
                        status = SableRetirementStatus.RETIRED;
                    }
                }
                if (status == SableRetirementStatus.RETIRED) {
                    status = SableRetirementStatus.MARK_RETIREMENT_FAILED;
                    if (LiveStructureProvenance.remove(server, reference, entry.metadata().creationOperationId())) {
                        status = SableRetirementStatus.JOURNAL_COMPLETION_FAILED;
                        state.journal.complete(entry);
                        status = SableRetirementStatus.RETIRED;
                    }
                }
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("Sable permanent deletion remains pending: {}", reference, failure);
            }
            state.progress.record(reference, status, server.getTickCount(), status != SableRetirementStatus.RETIRED);
            break;
        }
    }

    private static State state(MinecraftServer server) {
        SableStructureAccess.requireThread(server);
        return SERVERS.computeIfAbsent(server, value -> {
            try {
                var journal = new SableProvenanceRetirements(value.getWorldPath(LevelResource.ROOT)
                        .resolve("data/academy/sable_provenance_retirements"));
                if (!journal.healthy()) AcademyCraft.LOGGER.error("Sable retirement journal is corrupt; marked bodies remain isolated");
                return new State(journal);
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("Sable retirement journal unavailable; marked bodies remain isolated", failure);
                return new State(null);
            }
        });
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
    private static final class State {
        final SableProvenanceRetirements journal;
        final SableRetirementProgress progress = new SableRetirementProgress();
        boolean ledgerAvailable;
        boolean ledgerObserved;
        Iterator<SableProvenanceRetirements.Entry> iterator;
        State(SableProvenanceRetirements journal) { this.journal = journal; }
    }
}
