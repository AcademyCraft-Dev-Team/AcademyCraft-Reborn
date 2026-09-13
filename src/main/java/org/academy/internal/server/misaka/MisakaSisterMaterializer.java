package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.jspecify.annotations.Nullable;

/**
 * Recreates a living {@link MisakaSisterEntity} for a roster row whose entity was lost
 * (e.g. pre-persistence despawn) while preserving serial / uuid / incap / favor.
 * Invoked only from admin commands ({@code /academy misaka verify}), not on a server tick.
 */
public final class MisakaSisterMaterializer {
    /** Cap per command pass so a single verify stays bounded. */
    public static final int COMMAND_BUDGET = 512;

    private MisakaSisterMaterializer() {
    }

    /**
     * Spawn at last-known position when that chunk is loaded and no matching entity exists.
     * @return the new (or existing rebound) entity, or null if skipped
     */
    public static @Nullable MisakaSisterEntity tryMaterializeAtLastKnown(
            MinecraftServer server,
            MisakaSisterRecord record
    ) {
        if (MisakaLoadedSisterIndex.get(record.misakaUuid, record.serial) != null) {
            return null;
        }
        var chunk = hintChunk(record);
        if (chunk == null) {
            return null;
        }
        var level = sampleLevel(server, record);
        if (!level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
            return null;
        }

        var stray = findStrayInChunk(level, chunk, record);
        if (stray != null) {
            MisakaSisterRosterSync.bindToRecord(stray, record);
            MisakaLoadedSisterIndex.put(stray);
            return stray;
        }

        var sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (sister == null) {
            AcademyCraft.LOGGER.error("Misaka materialize: failed to create entity for #{}", record.serial);
            return null;
        }

        var feet = spawnFeet(level, record, chunk);
        sister.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0f, 0.0f);
        sister.setPersistenceRequired();
        sister.bindToRecord(record);
        if (!level.addFreshEntity(sister)) {
            AcademyCraft.LOGGER.error("Misaka materialize: addFreshEntity failed for #{}", record.serial);
            return null;
        }
        MisakaSisterRosterSync.updateLastKnownChunk(sister);
        MisakaLoadedSisterIndex.put(sister);
        return sister;
    }

    /**
     * Force-spawn at an explicit position (admin command). Still refuses if already loaded.
     */
    public static @Nullable MisakaSisterEntity materializeAt(
            ServerLevel level,
            MisakaSisterRecord record,
            double x,
            double y,
            double z,
            float yRot
    ) {
        if (MisakaLoadedSisterIndex.get(record.misakaUuid, record.serial) != null) {
            return null;
        }
        var sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (sister == null) {
            return null;
        }
        sister.snapTo(x, y, z, yRot, 0.0f);
        sister.setPersistenceRequired();
        sister.bindToRecord(record);
        if (!level.addFreshEntity(sister)) {
            return null;
        }
        MisakaSisterRosterSync.updateLastKnownChunk(sister);
        MisakaLoadedSisterIndex.put(sister);
        return sister;
    }

    static @Nullable ChunkPos hintChunk(MisakaSisterRecord record) {
        if (record.lastKnownChunk != null) {
            return record.lastKnownChunk;
        }
        if (record.lastKnownBlockPos != null) {
            return ChunkPos.containing(record.lastKnownBlockPos);
        }
        return null;
    }

    static ServerLevel sampleLevel(MinecraftServer server, MisakaSisterRecord record) {
        var level = server.getLevel(record.lastKnownDimension);
        return level != null ? level : server.overworld();
    }

    private static BlockPos spawnFeet(ServerLevel level, MisakaSisterRecord record, ChunkPos chunk) {
        if (record.lastKnownBlockPos != null) {
            return record.lastKnownBlockPos;
        }
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static @Nullable MisakaSisterEntity findStrayInChunk(
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
