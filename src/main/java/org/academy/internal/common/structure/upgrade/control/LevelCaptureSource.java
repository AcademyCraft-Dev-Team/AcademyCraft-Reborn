package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureCaptureRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bounded, loaded-only read of the host cells a capture would remove. Chunk tickets are never
 * requested: an unloaded source cell reports absence instead of forcing terrain to load, and
 * the caller turns that into a pending preview rather than an empty one. The same read produces
 * the authoritative source digest and the selection's real collision cuboids.
 */
final class LevelCaptureSource {
    private LevelCaptureSource() {
    }

    /** Local boxes are relative to the minimum corner; position cells stay in host coordinates. */
    record Snapshot(CaptureSourceSnapshot source, List<AABB> localBoxes, Vec3 localCenterOfMass) {
        Snapshot {
            localBoxes = List.copyOf(localBoxes);
        }

        boolean hasCollisionGeometry() {
            return !localBoxes.isEmpty();
        }
    }

    static Optional<Snapshot> read(ServerLevel host, StructureCaptureRequest request) {
        var view = new LoadedChunkView(host);
        var origin = origin(request.positions());
        var cells = new ArrayList<CaptureSourceSnapshot.Cell>(request.positions().size());
        var boxes = new ArrayList<AABB>();
        AABB union = null;
        try {
            for (var position : request.positions()) {
                var state = view.getBlockState(position);
                cells.add(new CaptureSourceSnapshot.Cell(position, state.toString()));
                for (var box : state.getCollisionShape(view, position).toAabbs()) {
                    var local = box.move(-origin.getX(), -origin.getY(), -origin.getZ());
                    boxes.add(local);
                    union = union == null ? local : union.minmax(local);
                }
            }
        } catch (LoadedChunkView.Unavailable unavailable) {
            return Optional.empty();
        }
        var center = union == null ? new Vec3(0.5, 0.5, 0.5) : union.getCenter();
        return Optional.of(new Snapshot(CaptureSourceSnapshot.of(cells), boxes, center));
    }

    /** Minimum corner of the selection, matching the capture transaction's own origin. */
    static BlockPos origin(List<BlockPos> positions) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (var position : positions) {
            x = Math.min(x, position.getX());
            y = Math.min(y, position.getY());
            z = Math.min(z, position.getZ());
        }
        return new BlockPos(x, y, z);
    }
}
