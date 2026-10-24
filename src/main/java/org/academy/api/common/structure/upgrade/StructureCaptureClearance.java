package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.internal.common.structure.upgrade.control.CaptureSoftClearance;

/** Explicit skill action; planning APIs themselves never remove obstacles. */
public final class StructureCaptureClearance {
    private StructureCaptureClearance() {}

    /**
     * Explicit capture action, shared by skills and nodes. Soft non-full source cells (snow
     * layers, grass, crops) are broken through normal permission/break/drop events, up to the
     * request's 256-cell bound; admitted solid cells, including grass blocks and leaves, retain their state.
     * The returned request, including its creation metadata, must be used for both preview and
     * capture. A denied break never authorizes silently omitting its cell from the structure.
     */
    public static StructureOperation.Result<StructureCaptureRequest> prepareSelection(
            ServerLevel host, Entity actor, StructureCaptureRequest capture) {
        return CaptureSoftClearance.prepareSelection(host, actor, capture);
    }

    /**
     * Clear at most 48 eligible soft cells along the source's vertical sweep. Source cells are
     * preserved. Permissions and normal break/drop events remain authoritative. A positive count
     * requires a fresh preview; it does not certify complete clearance or authorize capture.
     */
    public static int clearSoftObstacles(ServerLevel host, Entity actor, StructureCaptureRequest capture,
                                         double liftHeight) {
        return CaptureSoftClearance.clear(host, actor, capture, liftHeight);
    }
}
