package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.academy.internal.common.structure.physics.NativeStructureTerrainCache;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Same-thread mutations confined to the already guarded, disposable WorldSmoke fixture. */
final class NativeStructureTerrainCacheSmokeChecks {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int BUDGET = NativePhysicsWorld.MAX_BOXES;

    private NativeStructureTerrainCacheSmokeChecks() {}

    static BlockPos run(ServerLevel host, AABB bounds, BlockPos base, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke") && host.getServer().isSameThread(),
                "Terrain cache mutation checks require the explicit server-thread physics fixture");
        var center = bounds.getCenter(); var origin = new PhysicsVector(center.x, center.y, center.z);
        var cache = new NativeStructureTerrainCache(host, bounds, origin);
        var position = interiorAirCell(host, base);
        var original = host.getBlockState(position);
        var chunk = host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        require(chunk != null, "Cache fixture must use an already loaded chunk");
        var section = chunk.getSection(chunk.getSectionIndex(position.getY()));
        int x = position.getX() & 15, y = position.getY() & 15, z = position.getZ() & 15;
        boolean directMutation = false;
        try {
            var cold = cache.capture(BUDGET); assertOracle(host, bounds, origin, cold);
            var first = cache.diagnostics();
            require(first.partitions() > 1 && first.captures() == first.partitions() && first.scannedCells() > 0,
                    "Cold capture must actually extract multiple bounded partitions: " + first);
            var warm = cache.capture(BUDGET); assertOracle(host, bounds, origin, warm);
            var afterWarm = cache.diagnostics();
            require(afterWarm.captures() == first.captures() && afterWarm.scannedCells() == first.scannedCells()
                            && afterWarm.hits() >= first.hits() + first.partitions(),
                    "Warm capture re-extracted static terrain: " + first + " -> " + afterWarm);

            require(host.setBlock(position, Blocks.STONE.defaultBlockState(), FLAGS), "Cannot change the audited host test cell");
            require(!warm.stillValid(), "Normal host mutation did not invalidate its prior terrain receipt");
            var changed = cache.capture(BUDGET); assertOracle(host, bounds, origin, changed);
            assertOneDirtyPartition(afterWarm, cache.diagnostics(), "normal host write");
            require(changed.boxes().size() == cold.boxes().size() + 1, "AIR to STONE did not add its actual collision box");
            require(host.setBlock(position, original, FLAGS), "Cannot restore host cell after normal write");
            var restored = cache.capture(BUDGET); assertOracle(host, bounds, origin, restored);
            require(multiset(restored.boxes()).equals(multiset(cold.boxes())), "Normal-write rollback changed terrain geometry");

            var beforeRaw = cache.diagnostics();
            require(section.setBlockState(x, y, z, Blocks.STONE.defaultBlockState()).equals(original), "Direct-section fixture was not AIR");
            directMutation = true;
            require(!restored.stillValid(), "AIR to STONE bypassing LevelChunk hooks escaped the state receipt");
            var raw = cache.capture(BUDGET); assertOracle(host, bounds, origin, raw);
            assertOneDirtyPartition(beforeRaw, cache.diagnostics(), "direct LevelChunkSection write");
            require(raw.boxes().size() == cold.boxes().size() + 1, "Direct-section mutation reused stale empty collision data");
            require(section.setBlockState(x, y, z, original).is(Blocks.STONE), "Direct-section fixture changed unexpectedly");
            directMutation = false;
            var repaired = cache.capture(BUDGET); assertOracle(host, bounds, origin, repaired);
            require(multiset(repaired.boxes()).equals(multiset(cold.boxes())), "Direct-section rollback changed terrain geometry");

            int partitions = cache.diagnostics().partitions();
            require(!repaired.boxes().isEmpty(), "Budget fixture requires actual platform collision boxes");
            expectGeometryFailure(() -> cache.capture(repaired.boxes().size() - 1), NativeStructurePhysicsGeometry.Failure.LIMIT_EXCEEDED);
            require(cache.diagnostics().partitions() == partitions && repaired.stillValid(), "A smaller budget damaged committed cache receipts");
            assertOracle(host, bounds, origin, cache.capture(BUDGET));

            var border = host.getWorldBorder(); double originalSize = border.getSize();
            try {
                border.setSize(1);
                expectGeometryFailure(() -> cache.capture(BUDGET), NativeStructurePhysicsGeometry.Failure.INVALID_BOUNDS);
                require(cache.diagnostics().partitions() == partitions, "World-border denial damaged committed cache partitions");
            } finally { border.setSize(originalSize); }
            assertOracle(host, bounds, origin, cache.capture(BUDGET));

            var beforeClear = cache.diagnostics(); cache.clear();
            var cleared = cache.diagnostics();
            require(cleared.partitions() == 0 && cleared.captures() == beforeClear.captures()
                            && cleared.hits() == beforeClear.hits() && cleared.scannedCells() == beforeClear.scannedCells(),
                    "Clear must release receipts while retaining cumulative work counters");
            assertOracle(host, bounds, origin, cache.capture(BUDGET));
            require(cache.diagnostics().captures() == beforeClear.captures() + first.partitions()
                            && cache.diagnostics().scannedCells() == beforeClear.scannedCells() + first.scannedCells(),
                    "Clear did not cause a complete fresh bounded extraction");

            int farX = base.getX() + 32768;
            var far = new BlockPos(farX, base.getY(), base.getZ());
            var farBounds = new AABB(farX - 4, base.getY() - 2, base.getZ() - 4, farX + 4, base.getY() + 6, base.getZ() + 4);
            require(!host.hasChunkAt(far), "Missing-chunk cache fixture must begin unloaded");
            assertMissingChunks(host, farBounds);
            var farCenter = farBounds.getCenter();
            var missing = new NativeStructureTerrainCache(host, farBounds, new PhysicsVector(farCenter.x, farCenter.y, farCenter.z));
            expectGeometryFailure(() -> missing.capture(BUDGET), NativeStructurePhysicsGeometry.Failure.UNLOADED_CHUNK);
            require(!host.hasChunkAt(far) && missing.diagnostics().partitions() == 0, "Failed cache capture loaded or committed missing terrain");
            assertMissingChunks(host, farBounds);
            missing.clear();
            evidence.append("terrain_cache_direct_oracle_multiset=true\nterrain_cache_warm_reuses_partitions=true\n")
                    .append("terrain_cache_single_dirty_partition=true\nterrain_cache_direct_section_write_detected=true\n")
                    .append("terrain_cache_smaller_budget_rejected=true\nterrain_cache_border_rechecked=true\n")
                    .append("terrain_cache_clear_rebuild=true\nterrain_cache_missing_chunk_no_load=true\n")
                    .append("terrain_cache_initial_partitions=").append(first.partitions()).append("\nterrain_cache_initial_scanned_cells=")
                    .append(first.scannedCells()).append("\nterrain_cache_test_cell=").append(position).append('\n');
            return position;
        } finally {
            // Direct writes deliberately skip the production hook; restore their exact original state
            // through the same API before the fixture continues or reports any failure.
            if (!section.getBlockState(x, y, z).equals(original)) {
                if (directMutation) section.setBlockState(x, y, z, original);
                else require(host.setBlock(position, original, FLAGS), "Failed cache check could not restore its normal host write");
            }
            cache.clear();
        }
    }

    private static BlockPos interiorAirCell(ServerLevel host, BlockPos base) {
        for (var mutable : BlockPos.betweenClosed(base.offset(-8, 3, -8), base.offset(8, 8, 8))) {
            int x = mutable.getX() & 15, y = mutable.getY() & 15, z = mutable.getZ() & 15;
            if (x < 2 || x > 13 || y < 2 || y > 13 || z < 2 || z > 13) continue;
            if (Math.abs(mutable.getX() - base.getX()) < 6 && Math.abs(mutable.getZ() - base.getZ()) < 6) continue;
            require(host.hasChunkAt(mutable), "Cache test cell was not already loaded");
            if (host.getBlockState(mutable).isAir() && host.getBlockEntity(mutable) == null) return mutable.immutable();
        }
        throw new IllegalStateException("Isolated terrain has no safe partition-interior AIR cell");
    }

    private static void assertOneDirtyPartition(NativeStructureTerrainCache.Diagnostics before,
                                              NativeStructureTerrainCache.Diagnostics after, String operation) {
        require(after.partitions() == before.partitions() && after.captures() == before.captures() + 1
                        && after.hits() >= before.hits() + before.partitions() - 1
                        && after.scannedCells() > before.scannedCells(),
                operation + " did not limit extraction to its single dirty partition: " + before + " -> " + after);
    }

    private static void assertOracle(ServerLevel host, AABB bounds, PhysicsVector origin, NativeStructurePhysicsGeometry.Snapshot actual) {
        var expected = NativeStructurePhysicsGeometry.captureTerrain(host, bounds, origin, BUDGET);
        require(actual.stillValid() && expected.stillValid() && multiset(actual.boxes()).equals(multiset(expected.boxes())),
                "Cached terrain differs from direct real-world collision extraction");
    }

    private static void assertMissingChunks(ServerLevel host, AABB bounds) {
        // Include extraction's one-cell halo and the additional allowed neighbour-read ring.
        for (int x = ((int) Math.floor(bounds.minX) - 2) >> 4; x <= ((int) Math.floor(bounds.maxX) + 2) >> 4; x++) {
            for (int z = ((int) Math.floor(bounds.minZ) - 2) >> 4; z <= ((int) Math.floor(bounds.maxZ) + 2) >> 4; z++) {
                require(!host.hasChunkAt(new BlockPos(x * 16, (int) bounds.minY, z * 16)),
                        "Missing-chunk cache fixture loaded a sampled or neighbour chunk at " + x + "," + z);
            }
        }
    }

    private static Map<PhysicsBox, Integer> multiset(List<PhysicsBox> boxes) {
        var result = new HashMap<PhysicsBox, Integer>(); for (var box : boxes) result.merge(box, 1, Integer::sum); return result;
    }

    private static void expectGeometryFailure(Runnable action, NativeStructurePhysicsGeometry.Failure failure) {
        try { action.run(); }
        catch (NativeStructurePhysicsGeometry.GeometryException rejected) {
            require(rejected.failure() == failure, "Unexpected geometry rejection: " + rejected.failure() + " instead of " + failure); return;
        }
        throw new IllegalStateException("Expected geometry rejection: " + failure);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
