package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Pending transfer sources retain storage identity, but cannot tick, collide, track or be selected. */
public final class SableRestorationIsolation {
    private static final Set<ServerSubLevel> ISOLATED = Collections.newSetFromMap(new IdentityHashMap<>());

    private SableRestorationIsolation() {}

    /** Mixin boundary keeps the original list and raw identities on the normal hot path. */
    public static List<Object> visibleRaw(List<Object> sources) {
        if (sources.isEmpty()) return sources;
        var first = SableReflection.optional(ServerSubLevel.class, sources.getFirst());
        if (first == null || ISOLATED.isEmpty() && !MarkedGridRestoration.hasPending(first.getLevel().getServer())) return sources;
        var visible = sources.stream().filter(source -> !hidden(
                SableReflection.wrap(SubLevel.class, source))).toList();
        return visible.size() == sources.size() ? sources : visible;
    }

    public static boolean hidden(SubLevel source) {
        if (!(source instanceof ServerSubLevel ship)) return false;
        return ISOLATED.contains(ship) || MarkedGridRestoration.hasPending(ship.getLevel().getServer())
                && MarkedGridRestoration.pending(ship.getLevel().getServer(), reference(ship));
    }

    public static boolean detached(SubLevel source) {
        return source instanceof ServerSubLevel ship && ISOLATED.contains(ship);
    }

    public static List<SubLevel> visible(List<SubLevel> sources) {
        // The usual no-pending path reuses the backend's own list without allocating a copy.
        if (sources.isEmpty() || !(sources.getFirst() instanceof ServerSubLevel first)) return sources;
        if (ISOLATED.isEmpty() && !MarkedGridRestoration.hasPending(first.getLevel().getServer())) return sources;
        if (sources.stream().noneMatch(SableRestorationIsolation::hidden)) return sources;
        return sources.stream().filter(source -> !hidden(source)).toList();
    }

    /** An incomplete ownership transfer keeps its original plot reserved until its journal is published. */
    public static void reservePendingPlots(SubLevelContainer container) {
        if (!(container instanceof ServerSubLevelContainer serverContainer)) return;
        var host = serverContainer.getLevel();
        var reservations = MarkedGridRestoration.sourceReservations(host.getServer(), SableStructureAccess.ID,
                host.dimension().identifier());
        int side = 1 << container.getLogSideLength();
        for (var evidence : reservations) {
            int x = evidence.getInt("plot_x").orElse(-1);
            int z = evidence.getInt("plot_z").orElse(-1);
            if (x >= 0 && x < side && z >= 0 && z < side) {
                container.getOccupancy().set(container.getIndex(x, z));
            }
        }
    }

    /** Called before any source/observer tick, including the first tick after deserialization. */
    public static void beforeTick(SubLevelContainer container, List<SubLevel> sources) {
        if (!(container instanceof ServerSubLevelContainer serverContainer)) return;
        if (ISOLATED.isEmpty() && !MarkedGridRestoration.hasPending(serverContainer.getLevel().getServer())) return;
        for (var source : sources) {
            if (!(source instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
            boolean pending = MarkedGridRestoration.pending(ship.getLevel().getServer(), reference(ship));
            if (pending) isolate(ship.getLevel(), ship);
            else if (ISOLATED.contains(ship)) resume(serverContainer, ship);
        }
        ISOLATED.removeIf(ship -> ship.getLevel() == container.getLevel()
                && (ship.isRemoved() || container.getSubLevel(ship.getUniqueId()) != ship));
    }

    public static boolean isolate(ServerLevel host, ServerSubLevel ship) {
        SableStructureAccess.requireThread(host.getServer());
        if (ship.getLevel() != host || ship.isRemoved()) return false;
        if (ISOLATED.contains(ship)) return true;
        var container = SubLevelContainer.getContainer(host);
        if (container == null || container.getSubLevel(ship.getUniqueId()) != ship) return false;
        container.physicsSystem().getPipeline().remove(ship);
        // Mark before tracking callback so later backend removal cannot remove the native body twice.
        ISOLATED.add(ship);
        container.trackingSystem().onSubLevelRemoved(ship, SubLevelRemovalReason.UNLOADED);
        ship.getTrackingPlayers().clear();
        SableStructureAccess.invalidateActivation(host, ship.getUniqueId());
        return true;
    }

    private static void resume(ServerSubLevelContainer container, ServerSubLevel ship) {
        var pipeline = container.physicsSystem().getPipeline();
        // Equivalent to Sable's recovery rebuild, without issuing a second native remove.
        try {
            ship.buildMassTracker();
            pipeline.add(ship, ship.logicalPose());
            for (var holder : ship.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                var sections = chunk.getSections();
                for (int index = 0; index < chunk.getSectionsCount(); index++) {
                    var section = sections[index];
                    if (!section.hasOnlyAir()) pipeline.handleChunkSectionAddition(section,
                            chunk.getPos().x(), chunk.getSectionYFromSectionIndex(index), chunk.getPos().z(), true);
                }
            }
        } catch (RuntimeException failure) {
            pipeline.remove(ship);
            org.academy.AcademyCraft.LOGGER.error("Restored Sable source remains isolated after body rebuild failed: {}", ship.getUniqueId(), failure);
            return;
        }
        SableStructureAccess.invalidateActivation(ship.getLevel(), ship.getUniqueId());
        ISOLATED.remove(ship);
        container.trackingSystem().onSubLevelAdded(ship);
    }

    public static void shutdown(MinecraftServer server) {
        ISOLATED.removeIf(ship -> ship.getLevel().getServer() == server);
    }

    private static StructureRef reference(ServerSubLevel ship) {
        return new StructureRef(ship.getUniqueId(), ship.getLevel().dimension().identifier(), SableStructureAccess.ID, 0);
    }
}
