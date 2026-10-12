package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructurePresentationStatus;

import java.util.HashMap;
import java.util.Map;

/** Bounded state-only mirror. Initial scans skip empty sections; ordinary ticks read dirty cells. */
public final class NativeStructureViewCache {
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    private boolean initialized;
    private boolean limited;
    private NativeStructureView view = NativeStructureView.EMPTY;
    private StructurePresentationStatus status = StructurePresentationStatus.EMPTY;

    public NativeStructureView view() { return view; }
    public StructurePresentationStatus status() { return status; }

    public void refresh(ServerLevel level) {
        var changes = NativeStructureChanges.take(level);
        if (initialized && !changes.fullRescan() && changes.positions().isEmpty()) return;
        if (!initialized || limited || changes.fullRescan()) scan(level);
        else {
            for (var position : changes.positions()) {
                var state = level.getBlockState(position);
                if (state.isAir()) cells.remove(position);
                else cells.put(position, state);
            }
            limited = cells.size() > NativeStructureView.MAX_CELLS;
            if (limited) cells.clear();
        }
        initialized = true;
        if (limited) status = StructurePresentationStatus.LIMIT_EXCEEDED;
        else if (cells.isEmpty()) status = StructurePresentationStatus.EMPTY;
        else if (cells.values().stream().anyMatch(state -> !NativeStructureView.isSupportedState(state))) {
            status = StructurePresentationStatus.UNSUPPORTED_CONTENT;
        }
        else status = StructurePresentationStatus.VISIBLE;
        view = status == StructurePresentationStatus.VISIBLE
                ? new NativeStructureView(cells.entrySet().stream()
                .map(cell -> new NativeStructureView.Cell(cell.getKey(), cell.getValue())).toList())
                : NativeStructureView.EMPTY;
    }

    private void scan(ServerLevel level) {
        cells.clear();
        limited = false;
        for (int chunkX = -2; chunkX < 2; chunkX++) for (int chunkZ = -2; chunkZ < 2; chunkZ++) {
            var chunk = level.getChunk(chunkX, chunkZ);
            var sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                var section = sections[sectionIndex];
                if (section.hasOnlyAir()) continue;
                int baseY = level.getMinY() + sectionIndex * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (state.isAir()) continue;
                    if (cells.size() == NativeStructureView.MAX_CELLS) {
                        limited = true;
                        cells.clear();
                        return;
                    }
                    cells.put(new BlockPos(chunkX * 16 + x, baseY + y, chunkZ * 16 + z), state);
                }
            }
        }
    }
}
