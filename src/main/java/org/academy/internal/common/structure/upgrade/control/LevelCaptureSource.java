package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.upgrade.StructureOperation;

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
                // The transaction persists block states only. Do not certify a digest that
                // silently drops inventories or other block-entity payloads.
                if (state.hasBlockEntity() || view.getBlockEntity(position) != null) {
                    throw new Rejected(StructureOperation.Status.UNSUPPORTED, "capture_source_block_entity_unsupported");
                }
                if (!state.getFluidState().isEmpty()) {
                    throw new Rejected(StructureOperation.Status.UNSUPPORTED, "capture_source_fluid_unsupported");
                }
                if (LoadedChunkView.inspectShape(() -> state.getDestroySpeed(view, position)) < 0.0f) {
                    throw new Rejected(StructureOperation.Status.DENIED, "capture_source_unbreakable");
                }
                if (host.getBlockTicks().hasScheduledTick(position, state.getBlock())) {
                    throw new Rejected(StructureOperation.Status.UNSUPPORTED, "capture_source_scheduled_tick_pending");
                }
                var stateId = state.toString();
                if (stateId.isBlank() || stateId.length() > CaptureSourceSnapshot.MAX_STATE_LENGTH) {
                    throw new Rejected(StructureOperation.Status.UNSUPPORTED, "capture_source_state_id_unsupported");
                }
                cells.add(new CaptureSourceSnapshot.Cell(position, stateId));
                for (var box : LoadedChunkView.inspectShape(() -> state.getCollisionShape(view, position).toAabbs())) {
                    if (boxes.size() >= ExtractionPlanner.MAX_LOCAL_BOXES) {
                        throw new Rejected(StructureOperation.Status.CONFLICT, "capture_source_shape_budget_exhausted");
                    }
                    var local = localBox(box, position, origin);
                    boxes.add(local);
                    union = union == null ? local : union.minmax(local);
                }
            }
        } catch (LoadedChunkView.Unavailable unavailable) {
            if (unavailable.budget) {
                throw new Rejected(StructureOperation.Status.CONFLICT, "capture_source_read_budget_exhausted");
            }
            return Optional.empty();
        } catch (LoadedChunkView.ShapeFailure unavailable) {
            throw new Rejected(StructureOperation.Status.UNSUPPORTED, "capture_source_geometry_unavailable");
        }
        if (!view.current()) {
            throw new Rejected(StructureOperation.Status.STALE, "capture_source_changed_during_preview");
        }
        var center = union == null ? new Vec3(0.5, 0.5, 0.5) : union.getCenter();
        return Optional.of(new Snapshot(CaptureSourceSnapshot.of(cells), boxes, center));
    }

    /** Vanilla shape cuboids start in block-local coordinates, not host coordinates. */
    static AABB localBox(AABB blockLocal, BlockPos position, BlockPos origin) {
        return blockLocal.move(position.getX() - origin.getX(), position.getY() - origin.getY(),
                position.getZ() - origin.getZ());
    }

    static final class Rejected extends RuntimeException {
        final StructureOperation.Status status;
        final String detail;

        Rejected(StructureOperation.Status status, String detail) {
            super(null, null, false, false);
            this.status = status;
            this.detail = detail;
        }
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
