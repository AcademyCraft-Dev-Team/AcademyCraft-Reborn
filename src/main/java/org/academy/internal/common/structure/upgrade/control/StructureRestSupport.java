package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Loaded host support only. A hovering, terrain-paused or merely side-wedged body is not grounded. */
public final class StructureRestSupport {
    private static final Vec3 DOWN = new Vec3(0, -.13, 0);
    private static final int MAX_TESTS = 8_192;
    private static final int MAX_CELLS = 2_048;

    private StructureRestSupport() {}

    public static boolean grounded(ServerLevel host, OrientationBackendAccess.Body body) {
        if (!body.terrainReady()) return false;
        var view = new LoadedChunkView(host);
        int cells = 0, tests = 0;
        try {
            var boxes = body.localBoxes().stream().map(local -> TranslationSweep.Box.of(local, body.motion().pose()))
                    .sorted(java.util.Comparator.comparingDouble(box -> box.bounds().minY)).toList();
            for (var box : boxes) {
                var bounds = box.bounds();
                // A tilted box can rest on a step under its higher end. The global AABB
                // minimum is not the height of every supporting contact.
                int minX = (int) Math.floor(bounds.minX) - 1, maxX = (int) Math.floor(bounds.maxX) + 1;
                int minZ = (int) Math.floor(bounds.minZ) - 1, maxZ = (int) Math.floor(bounds.maxZ) + 1;
                int minY = (int) Math.floor(bounds.minY - .10) - 1, maxY = (int) Math.floor(bounds.maxY + .03);
                long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                if (volume < 1 || volume > MAX_CELLS - cells) return false;
                for (var mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                    cells++;
                    var position = mutable.immutable();
                    var state = view.getBlockState(position);
                    for (var shape : LoadedChunkView.inspectShape(() -> state.getCollisionShape(view, position).toAabbs())) {
                        if (++tests > MAX_TESTS) return false;
                        var obstacle = shape.move(position);
                        if (supports(box, obstacle)) return view.current();
                    }
                }
            }
        } catch (LoadedChunkView.Unavailable | LoadedChunkView.ShapeFailure unavailable) {
            return false;
        }
        return false;
    }

    static boolean supports(TranslationSweep.Box body, net.minecraft.world.phys.AABB obstacle) {
        // Raise the query above contact slop, then require a new upward-facing contact.
        // Side walls and deep pre-existing overlaps still cannot establish floor support.
        var hit = TranslationSweep.firstHit(body.translated(new Vec3(0, .03, 0)), DOWN,
                TranslationSweep.Box.axisAligned(obstacle));
        return hit.isPresent() && !hit.get().initialOverlap() && hit.get().normal().y > .5;
    }

    public static boolean legacyClear(ServerLevel host, net.minecraft.world.phys.AABB targetBounds) {
        return OrientationWorldSnapshot.legacyClear(host, targetBounds);
    }
}
