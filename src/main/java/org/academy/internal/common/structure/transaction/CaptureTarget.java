package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;

import java.util.List;
import java.util.Optional;

/**
 * Server-thread executor boundary for one capture target backend. A transaction selects one
 * implementation at admission, allocates its target and then drives every staged read, write,
 * geometry and persistence step through it; the coordinator itself never addresses a backend.
 */
public interface CaptureTarget {
    /**
     * Allocates a target for the selected origin and accepts the first content cell in capture
     * order. A backend that can make the target non-empty in the same call chain writes that
     * seed before returning so a zero-mass allocation window never exists; a backend that
     * ignores it lets the coordinator fill the cell during the copy stage instead.
     */
    Optional<StructureRef> allocate(ServerLevel host, BlockPos origin, BlockPos seedOffset, BlockState seedState);

    /**
     * Exact mass the given source cells will have as a target body when this backend can predict
     * it before allocating; NaN tells the caller to keep its own post-capture gate.
     */
    default double estimateMass(ServerLevel host, List<BlockPos> positions) {
        return Double.NaN;
    }

    /** Maximum number of structure cells this backend can hold for one target. */
    int cellBudget();

    /** Whether the target currently resolves to a handle that accepts cell writes. */
    boolean ready(StructureRef target);

    /** Writes one previously empty cell and returns the backend write outcome. */
    StructureWriteResult writeCell(StructureRef target, BlockPos localOffset, BlockState state);

    /** Read-only verification of one written cell; empty when the backend cannot expose content. */
    Optional<BlockState> readCell(StructureRef target, BlockPos localOffset);

    /** Clears one previously written cell back to air after validating the expected state. */
    StructureWriteResult clearCell(StructureRef target, BlockPos localOffset, BlockState expected);

    /** Whether the backend suspended this target after an uncertain write. */
    boolean quarantined(StructureRef target);

    /** Best-effort deactivation; callers log failures and continue. */
    void deactivate(StructureRef target);

    /** Persists a checkpoint; false keeps the transaction retained for recovery. */
    boolean save(StructureRef target);

    /**
     * Releases an uncommitted target during rollback or capacity reclamation; true means the
     * target no longer exists, so the coordinator must not clear its cells. The Sable backend
     * removes the whole ship because per-cell clearing trips its zero-mass removal.
     */
    boolean releaseEmpty(StructureRef target);

    /**
     * Resolves the target handle for the coordinator. A recovery transaction may reload an
     * unloaded target before deciding whether its lifecycle allows further work.
     */
    Optional<StructureHandle> resolve(StructureRef target, boolean recovery);

    /** Verifies the copied content against the source cell count and backend physics budgets. */
    boolean verifyCopy(StructureRef target, int expectedCells);

    Identifier backendId();
}
