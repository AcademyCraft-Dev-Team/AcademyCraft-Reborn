package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;

/** Shared node continuation: capture commit is not proof that the body escaped its source socket. */
public final class ProgramStructureExtraction {
    private ProgramStructureExtraction() {}

    public static ProgramStructureOperationResult poll(ServerPlayer actor, StructureRef reference) {
        if (!StructureCaptureExtractions.tracked(reference)) {
            return StructureCaptureExtractions.enabled()
                    ? ProgramStructureOperationResult.rejected("extraction_session_missing")
                    : ProgramStructureOperationResult.completed("committed", reference);
        }
        var handle = StructureCaptureExtractions.owned(actor, reference).orElse(null);
        if (handle == null) return ProgramStructureOperationResult.rejected("extraction_session_foreign_actor");
        var result = StructureCaptureExtractions.advance((ServerLevel) actor.level(), actor, reference);
        if (StructureCaptureExtractions.arrived(result)) {
            StructureCaptureExtractions.release(handle);
            return ProgramStructureOperationResult.completed("extraction_clear", reference);
        }
        if (StructureCaptureExtractions.failed(result)) {
            StructureCaptureExtractions.release(handle);
            return ProgramStructureOperationResult.rejected(result.detail());
        }
        return ProgramStructureOperationResult.pending(result.detail());
    }
}
