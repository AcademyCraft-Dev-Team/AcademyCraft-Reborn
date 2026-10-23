package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureRef;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral evidence retained only after a live capture publishes and recycles its journal.
 * The original request instance joins a preview to that exact submission; reconstructed equal
 * requests and recovered transactions cannot impersonate an earlier preview. This does not
 * authenticate an actor: the preview owner and current control lease must be checked separately.
 */
public record StructureCaptureEvidence(Identifier hostDimension, StructureRef target,
                                       StructureCaptureRequest request, List<Cell> cells,
                                       UUID runtimeId, long contentRevision) {
    public record Cell(BlockPos position, String state) {
        public Cell {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(state, "state");
        }
    }

    public StructureCaptureEvidence {
        Objects.requireNonNull(hostDimension, "hostDimension");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(runtimeId, "runtimeId");
        cells = List.copyOf(cells);
        if (!target.dimension().equals(hostDimension) || cells.isEmpty()
                || cells.size() > StructureCaptureRequest.MAXIMUM_BLOCKS || contentRevision < 0) {
            throw new IllegalArgumentException("Invalid completed capture evidence");
        }
    }

    public boolean matches(Identifier host, StructureRef reference, StructureCaptureRequest previewRequest) {
        return hostDimension.equals(host) && target.equals(reference) && request == previewRequest
                && request.physical();
    }
}
