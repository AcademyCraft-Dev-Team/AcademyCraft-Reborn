package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;

import java.util.Optional;

/**
 * Server-thread executor boundary for one capture target backend. A transaction selects one
 * implementation at admission, allocates its target and then drives every staged read, write,
 * geometry and persistence step through it; the coordinator itself never addresses a backend.
 */
public interface CaptureTarget {
    /** Allocates an empty target for the selected origin, or empty when this backend cannot serve it. */
    Optional<StructureRef> allocate(ServerLevel host, BlockPos origin);

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

    /** Releases the capacity of an unloaded empty target; false keeps the slot consumed. */
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
