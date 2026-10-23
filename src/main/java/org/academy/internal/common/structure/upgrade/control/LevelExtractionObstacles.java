package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded host-shape snapshot for one capture preview. The source selection is excluded because
 * those cells are about to leave the world, not because they are air. Neighbour cells are
 * included so fences, walls and similar multi-cell shapes are not under-reported, and only
 * loaded chunks are read: an unknown section is never reported as an empty one.
 *
 * <p>Foreign rigid bodies are not modelled here. Any already loaded body inside the swept
 * envelope makes the whole snapshot UNKNOWN, which fails the preview conservatively instead of
 * certifying a path through another structure's solver state.</p>
 */
final class LevelExtractionObstacles implements ExtractionPlanner.ObstacleSnapshot {
    static final int MAX_CELLS = 16_384;
    private final LoadedChunkView view;
    private final Set<Long> excluded = new HashSet<>();
    private final long revision;
    private final boolean complete;
    private int cellsRead;

    LevelExtractionObstacles(ServerLevel host, List<BlockPos> excluded, AABB envelope, long revision,
                             @Nullable StructureRef controlled) {
        this.view = new LoadedChunkView(host);
        this.revision = revision;
        for (var position : excluded) this.excluded.add(position.asLong());
        this.complete = foreignBodiesClear(host, envelope, controlled);
    }

    @Override
    public ExtractionPlanner.Query query(AABB bounds, int maximumShapes) {
        if (!complete) return unknown();
        int minX = (int) Math.floor(bounds.minX) - 1, minY = (int) Math.floor(bounds.minY) - 1;
        int minZ = (int) Math.floor(bounds.minZ) - 1, maxX = (int) Math.floor(bounds.maxX) + 1;
        int maxY = (int) Math.floor(bounds.maxY) + 1, maxZ = (int) Math.floor(bounds.maxZ) + 1;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > MAX_CELLS) return truncated(List.of());
        var result = new ArrayList<ExtractionPlanner.Obstacle>();
        try {
            for (var mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                if (excluded.contains(mutable.asLong())) continue;
                if (++cellsRead > MAX_CELLS) return truncated(result);
                var position = mutable.immutable();
                var state = view.getBlockState(position);
                if (state.getCollisionShape(view, position).isEmpty()) {
                    if (state.getShape(view, position).isEmpty()) continue;
                    for (var shape : state.getShape(view, position).toAabbs()) {
                        if (result.size() >= maximumShapes) return truncated(result);
                        result.add(new ExtractionPlanner.Obstacle("soft:" + position.asLong(),
                                ExtractionPlanner.ObstacleKind.SOFT_REQUIRES_PERMISSION,
                                TranslationSweep.Box.axisAligned(shape.move(position))));
                    }
                    continue;
                }
                for (var box : state.getCollisionShape(view, position).toAabbs()) {
                    var world = box.move(position);
                    if (!world.intersects(bounds)) continue;
                    if (result.size() >= maximumShapes) return truncated(result);
                    result.add(new ExtractionPlanner.Obstacle("host:" + position.asLong(),
                            ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.axisAligned(world)));
                }
            }
        } catch (LoadedChunkView.Unavailable unavailable) {
            return unknown();
        }
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.COMPLETE, false, result);
    }

    /** The controlled body is not an obstacle to itself; every other loaded body is. */
    private static boolean foreignBodiesClear(ServerLevel host, AABB envelope, @Nullable StructureRef controlled) {
        for (var backend : StructureBackendRouter.orientationBackends(host)) {
            var nearby = backend.nearby(host, envelope, 2);
            if (!nearby.complete()) return false;
            for (var body : nearby.bodies()) {
                if (controlled == null || !body.reference().equals(controlled)) return false;
            }
        }
        return true;
    }

    private ExtractionPlanner.Query unknown() {
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.UNKNOWN, false, List.of());
    }

    private ExtractionPlanner.Query truncated(List<ExtractionPlanner.Obstacle> collected) {
        return new ExtractionPlanner.Query(revision, ExtractionPlanner.Coverage.COMPLETE, true, collected);
    }
}
