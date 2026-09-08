package org.academy.api.server.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import java.util.List;
import java.util.UUID;

/** Server-thread program access with the same ownership, Level 5 and capability checks as the GUI. */
public final class AbilityProgramService {
    private AbilityProgramService() {
    }

    public enum Status { COMPLETED, DEFERRED, REJECTED, FAILED }
    public record Result(Status status, int nodeId, String reason, List<ProgramDiagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        public boolean accepted() { return status == Status.COMPLETED || status == Status.DEFERRED; }
    }

    public static Result save(ServerPlayer player, int slot, AbilityProgram program) {
        return AbilityProgramManager.saveForAddon(player, slot, program);
    }
    public static ProgramBook book(ServerPlayer player) {
        return AbilityProgramManager.bookForAddon(player);
    }
    public static Result execute(ServerPlayer player, int slot, AbilityProgram program) {
        return AbilityProgramManager.executeForAddon(player, slot, program);
    }
    public static void cancel(ServerPlayer player, int slot, UUID programId) {
        AbilityProgramManager.cancelForAddon(player, slot, programId);
    }
}
