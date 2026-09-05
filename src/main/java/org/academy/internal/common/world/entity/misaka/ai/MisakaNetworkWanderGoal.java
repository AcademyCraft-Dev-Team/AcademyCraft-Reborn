package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class MisakaNetworkWanderGoal extends Goal {
    private static final int INNER_RADIUS_CHUNKS = 4;
    private static final int OUTER_RADIUS_CHUNKS = 8;
    private static final int RETURN_ANCHOR_RADIUS_CHUNKS = 2;
    private final MisakaSisterEntity sister;
    private boolean enabled = true;
    private int repathCooldown;

    public MisakaNetworkWanderGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean canUse() {
        if (!enabled || !sister.isAwakened() || sister.getWanderStyle() != WanderStyle.FREE_MOVE) {
            return false;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
            return sister.getNavigation().isInProgress();
        }
        return pickWanderTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return enabled && sister.isAwakened() && sister.getWanderStyle() == WanderStyle.FREE_MOVE
                && !sister.getNavigation().isDone();
    }

    @Override
    public void stop() {
        sister.getNavigation().stop();
        repathCooldown = 40;
    }

    private boolean pickWanderTarget() {
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            return false;
        }

        int chunkX;
        int chunkZ;
        // Unbound is treated as "too far from core": stay near wanderAnchorChunk.
        boolean farFromCore = record.networkNodePos == null
                || chebyshevDistance(sister.chunkPosition(), ChunkPos.containing(record.networkNodePos))
                > INNER_RADIUS_CHUNKS;
        if (farFromCore) {
            var fallback = record.networkNodePos != null
                    ? ChunkPos.containing(record.networkNodePos)
                    : sister.chunkPosition();
            var anchor = record.wanderAnchorChunk != null ? record.wanderAnchorChunk : fallback;
            chunkX = anchor.x() + sister.getRandom().nextInt(RETURN_ANCHOR_RADIUS_CHUNKS * 2 + 1)
                    - RETURN_ANCHOR_RADIUS_CHUNKS;
            chunkZ = anchor.z() + sister.getRandom().nextInt(RETURN_ANCHOR_RADIUS_CHUNKS * 2 + 1)
                    - RETURN_ANCHOR_RADIUS_CHUNKS;
            record.wanderAnchorChunk = new ChunkPos(chunkX, chunkZ);
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
        } else {
            var core = ChunkPos.containing(record.networkNodePos);
            List<ChunkPos> bedChunks = findBedChunks(core, INNER_RADIUS_CHUNKS);
            if (!bedChunks.isEmpty()) {
                ChunkPos bedChunk = bedChunks.get(sister.getRandom().nextInt(bedChunks.size()));
                chunkX = bedChunk.x() + sister.getRandom().nextInt(OUTER_RADIUS_CHUNKS * 2 + 1) - OUTER_RADIUS_CHUNKS;
                chunkZ = bedChunk.z() + sister.getRandom().nextInt(OUTER_RADIUS_CHUNKS * 2 + 1) - OUTER_RADIUS_CHUNKS;
            } else {
                chunkX = core.x() + sister.getRandom().nextInt(INNER_RADIUS_CHUNKS * 2 + 1) - INNER_RADIUS_CHUNKS;
                chunkZ = core.z() + sister.getRandom().nextInt(INNER_RADIUS_CHUNKS * 2 + 1) - INNER_RADIUS_CHUNKS;
            }
        }

        int x = chunkX * 16 + sister.getRandom().nextInt(16);
        int z = chunkZ * 16 + sister.getRandom().nextInt(16);
        int y = sister.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return sister.getNavigation().moveTo(x + 0.5, y, z + 0.5, 0.6);
    }

    private List<ChunkPos> findBedChunks(ChunkPos core, int radius) {
        var beds = new java.util.HashSet<ChunkPos>();
        var level = sister.level();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunkPos = new ChunkPos(core.x() + dx, core.z() + dz);
                if (!level.hasChunk(chunkPos.x(), chunkPos.z())) {
                    continue;
                }
                if (chunkHasBed(chunkPos)) {
                    beds.add(chunkPos);
                }
            }
        }
        if (beds.isEmpty()) {
            return List.of();
        }
        // 4-connected component of bed chunks that touches the core chunk.
        var connected = new ArrayList<ChunkPos>();
        var queue = new java.util.ArrayDeque<ChunkPos>();
        var seen = new java.util.HashSet<ChunkPos>();
        for (ChunkPos bed : beds) {
            if (chebyshevDistance(bed, core) <= 1) {
                queue.add(bed);
                seen.add(bed);
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            ChunkPos current = queue.poll();
            connected.add(current);
            for (int[] dir : dirs) {
                ChunkPos next = new ChunkPos(current.x() + dir[0], current.z() + dir[1]);
                if (!beds.contains(next) || !seen.add(next)) {
                    continue;
                }
                if (chebyshevDistance(next, core) > radius) {
                    continue;
                }
                queue.add(next);
            }
        }
        return connected;
    }

    private boolean chunkHasBed(ChunkPos chunkPos) {
        var level = sister.level();
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                int y = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        worldX,
                        worldZ
                );
                int minY = Math.max(level.getMinY(), y - 8);
                int maxY = Math.min(level.getMaxY(), y + 8);
                for (int worldY = minY; worldY <= maxY; worldY++) {
                    if (level.getBlockState(new BlockPos(worldX, worldY, worldZ)).is(BlockTags.BEDS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int chebyshevDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }
}
