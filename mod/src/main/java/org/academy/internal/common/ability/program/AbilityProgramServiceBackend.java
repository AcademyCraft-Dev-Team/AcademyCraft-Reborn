package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.server.ability.program.AbilityProgramService;

import java.util.UUID;

/**
 * Binds the public {@link AbilityProgramService} facade to the internal program manager.
 */
public final class AbilityProgramServiceBackend implements AbilityProgramService.Backend {
    @Override
    public AbilityProgramService.Result save(ServerPlayer player, int slot, AbilityProgram program) {
        return AbilityProgramManager.saveForAddon(player, slot, program);
    }

    @Override
    public ProgramBook book(ServerPlayer player) {
        return AbilityProgramManager.bookForAddon(player);
    }

    @Override
    public AbilityProgramService.Result execute(ServerPlayer player, int slot, AbilityProgram program) {
        return AbilityProgramManager.executeForAddon(player, slot, program);
    }

    @Override
    public void cancel(ServerPlayer player, int slot, UUID programId) {
        AbilityProgramManager.cancelForAddon(player, slot, programId);
    }
}
