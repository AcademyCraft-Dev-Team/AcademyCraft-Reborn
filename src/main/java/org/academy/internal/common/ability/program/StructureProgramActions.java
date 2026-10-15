package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Resource gate shared by immediate structure actions and the initial asynchronous capture. */
public final class StructureProgramActions {
    private StructureProgramActions() {
    }

    public static <T> T execute(ServerPlayer player, Skill skill, float cost,
                                Supplier<T> action, Predicate<T> accepted) {
        var result = new ArrayList<T>(1);
        var paid = AbilitySystemServer.getSystem(player).tryTimedOccupationIf(player, cost, skill, () -> {
            var value = action.get();
            result.add(value);
            return accepted.test(value);
        });
        if (result.isEmpty() || !paid && accepted.test(result.getFirst())) {
            throw new IllegalStateException("Insufficient CP for structure program action");
        }
        return result.getFirst();
    }
}
