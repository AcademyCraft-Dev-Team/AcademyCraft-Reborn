package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Shared read-only entity telemetry used by program query nodes.
 *
 * <p>Health is available for every living entity. CP and SP are player resources today; keeping
 * the dispatch in one public helper leaves one stable extension point when non-player ability
 * resource holders are introduced.</p>
 */
public final class ProgramEntityDataQuery {
    private ProgramEntityDataQuery() {
    }

    public static OptionalDouble query(
            Object entityReference,
            CommonProgramNodeCatalog.EntityDataKind data
    ) {
        Objects.requireNonNull(data, "data");
        return switch (data) {
            case HEALTH -> entityReference instanceof LivingEntity living
                    ? OptionalDouble.of(living.getHealth()) : OptionalDouble.empty();
            case CP -> entityReference instanceof ServerPlayer player
                    ? OptionalDouble.of(AbilitySystemServer.getSystem(player)
                    .getPlayerAvailableCP(player.getUUID()))
                    : OptionalDouble.empty();
            case SP -> entityReference instanceof ServerPlayer player
                    ? OptionalDouble.of(AbilitySystemServer.getSystem(player)
                    .getPlayerCurrSP(player.getUUID()))
                    : OptionalDouble.empty();
        };
    }
}
