package org.academy.api.server.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread program access with the same ownership, Level 5 and capability checks as the GUI.
 */
public final class AbilityProgramService {
    /**
     * Implementation-provided backend; registered once during common setup.
     */
    public interface Backend {
        Result save(ServerPlayer player, int slot, AbilityProgram program);

        ProgramBook book(ServerPlayer player);

        Result execute(ServerPlayer player, int slot, AbilityProgram program);

        void cancel(ServerPlayer player, int slot, UUID programId);
    }

    private static volatile @Nullable Backend backend;

    private AbilityProgramService() {
    }

    public static void registerBackend(Backend backend) {
        AbilityProgramService.backend = Objects.requireNonNull(backend);
    }

    public enum Status {COMPLETED, DEFERRED, REJECTED, FAILED}

    public record Result(Status status, int nodeId, String reason, List<ProgramDiagnostic> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean accepted() {
            return status == Status.COMPLETED || status == Status.DEFERRED;
        }
    }

    public static Result save(ServerPlayer player, int slot, AbilityProgram program) {
        return requireBackend().save(player, slot, program);
    }

    public static ProgramBook book(ServerPlayer player) {
        return requireBackend().book(player);
    }

    public static Result execute(ServerPlayer player, int slot, AbilityProgram program) {
        return requireBackend().execute(player, slot, program);
    }

    public static void cancel(ServerPlayer player, int slot, UUID programId) {
        requireBackend().cancel(player, slot, programId);
    }

    private static Backend requireBackend() {
        var current = backend;
        if (current == null) throw new IllegalStateException("Ability program backend is not registered");
        return current;
    }
}
