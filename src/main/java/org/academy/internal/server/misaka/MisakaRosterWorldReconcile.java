package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Heals bidirectional drift between {@link MisakaSisterRoster} SavedData and living entities.
 * Run only from admin commands ({@code /academy misaka verify} / {@code prune-absent}), not on tick.
 * <ul>
 *   <li>Loaded entity, no roster row: {@link MisakaSisterRosterSync#ensureRegistered}.</li>
 *   <li>Roster row, no entity, last-known chunk loaded: rebind stray or
 *       {@link MisakaSisterMaterializer} rematerialize (despawn recovery).</li>
 * </ul>
 */
public final class MisakaRosterWorldReconcile {
    public record Report(
            int loadedMatched,
            int rebound,
            int rematerialized,
            int notCurrentlyLoaded,
            int pendingMaterialize,
            int prunedAbsent,
            int entitiesWithoutRosterFixed
    ) {
    }

    private MisakaRosterWorldReconcile() {
    }

    public static Report reconcile(MinecraftServer server, boolean pruneAbsent, boolean logDetails) {
        return reconcile(
                server,
                pruneAbsent,
                logDetails,
                MisakaSisterMaterializer.COMMAND_BUDGET,
                !pruneAbsent
        );
    }

    /**
     * @param autoMaterialize when true, recreate missing entities at last-known if that chunk is loaded
     * @param materializeBudget max new entities this pass (rate-limit for large rosters)
     */
    public static Report reconcile(
            MinecraftServer server,
            boolean pruneAbsent,
            boolean logDetails,
            int materializeBudget,
            boolean autoMaterialize
    ) {
        var roster = MisakaSisterRoster.get(server);
        int matched = 0;
        int rebound = 0;
        int rematerialized = 0;
        int notLoaded = 0;
        int pending = 0;
        int pruned = 0;
        int entityFixed = 0;
        int materializeUsed = 0;

        for (var sister : new ArrayList<>(MisakaLoadedSisterIndex.all())) {
            var before = sister.getMisakaUuid();
            MisakaSisterRosterSync.ensureRegistered(sister);
            if (before == null && sister.getMisakaUuid() != null) {
                entityFixed++;
            } else if (before != null && !before.equals(sister.getMisakaUuid())) {
                entityFixed++;
            }
        }

        var toPrune = new ArrayList<UUID>();
        for (var record : List.copyOf(roster.all())) {
            if (MisakaLoadedSisterIndex.get(record.misakaUuid, record.serial) != null) {
                matched++;
                continue;
            }

            var found = findStrayAnywhere(server, record);
            if (found != null) {
                MisakaSisterRosterSync.bindToRecord(found, record);
                rebound++;
                if (logDetails) {
                    AcademyCraft.LOGGER.info(
                            "Misaka reconcile: rebound entity to roster #{} ({}) at {}",
                            record.serial,
                            record.misakaUuid,
                            found.blockPosition()
                    );
                }
                matched++;
                continue;
            }

            notLoaded++;
            if (!isLastKnownChunkLoaded(server, record)) {
                continue;
            }

            if (autoMaterialize && materializeUsed < materializeBudget) {
                var spawned = MisakaSisterMaterializer.tryMaterializeAtLastKnown(server, record);
                if (spawned != null) {
                    materializeUsed++;
                    rematerialized++;
                    matched++;
                    notLoaded--;
                    if (logDetails) {
                        AcademyCraft.LOGGER.info(
                                "Misaka reconcile: rematerialized #{} ({}) at {}",
                                record.serial,
                                record.misakaUuid,
                                spawned.blockPosition()
                        );
                    }
                    continue;
                }
            }

            pending++;
            if (logDetails) {
                AcademyCraft.LOGGER.info(
                        "Misaka reconcile: #{} pending rematerialize at loaded chunk {} "
                                + "(budget exhausted or spawn failed)",
                        record.serial,
                        MisakaSisterMaterializer.hintChunk(record)
                );
            }
            if (pruneAbsent) {
                toPrune.add(record.misakaUuid);
            }
        }

        for (var uuid : toPrune) {
            if (roster.release(uuid)) {
                pruned++;
                if (logDetails) {
                    AcademyCraft.LOGGER.warn("Misaka reconcile: pruned absent roster uuid {}", uuid);
                }
            }
        }

        return new Report(matched, rebound, rematerialized, notLoaded, pending, pruned, entityFixed);
    }

    public static Report reconcile(MinecraftServer server, boolean pruneAbsent) {
        return reconcile(server, pruneAbsent, true);
    }

    private static boolean isLastKnownChunkLoaded(MinecraftServer server, MisakaSisterRecord record) {
        var chunk = MisakaSisterMaterializer.hintChunk(record);
        if (chunk == null) {
            return false;
        }
        var level = MisakaSisterMaterializer.sampleLevel(server, record);
        return level.getChunkSource().hasChunk(chunk.x(), chunk.z());
    }

    private static @org.jspecify.annotations.Nullable MisakaSisterEntity findStrayAnywhere(
            MinecraftServer server,
            MisakaSisterRecord record
    ) {
        var indexed = MisakaLoadedSisterIndex.get(record.misakaUuid, record.serial);
        if (indexed != null) {
            return indexed;
        }

        var chunk = MisakaSisterMaterializer.hintChunk(record);
        if (chunk != null) {
            var level = MisakaSisterMaterializer.sampleLevel(server, record);
            if (level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
                var inChunk = findStrayInChunk(level, chunk, record);
                if (inChunk != null) {
                    return inChunk;
                }
            }
        }

        for (var sister : MisakaLoadedSisterIndex.all()) {
            if (sister.isRemoved()) {
                continue;
            }
            if (record.misakaUuid.equals(sister.getMisakaUuid()) || sister.getSerial() == record.serial) {
                return sister;
            }
        }
        return null;
    }

    private static @org.jspecify.annotations.Nullable MisakaSisterEntity findStrayInChunk(
            ServerLevel level,
            ChunkPos chunk,
            MisakaSisterRecord record
    ) {
        var box = new AABB(
                chunk.getMinBlockX(),
                level.dimensionType().minY(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1,
                level.dimensionType().minY() + level.dimensionType().logicalHeight(),
                chunk.getMaxBlockZ() + 1
        );
        MisakaSisterEntity byUuid = null;
        MisakaSisterEntity bySerial = null;
        for (var sister : level.getEntitiesOfClass(MisakaSisterEntity.class, box)) {
            if (sister.isRemoved()) {
                continue;
            }
            if (record.misakaUuid.equals(sister.getMisakaUuid())) {
                byUuid = sister;
                break;
            }
            if (sister.getSerial() == record.serial) {
                bySerial = sister;
            }
        }
        return byUuid != null ? byUuid : bySerial;
    }
}
