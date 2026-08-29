package org.academy.api.server.team;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.server.team.AcademyTeamData;

/** Unified team relation covering vanilla {@code /team} and Academy {@code /teams}. */
public final class TeamRelations {
    private TeamRelations() {
    }

    public static boolean areTeammates(Entity first, Entity second) {
        if (first == null || second == null) return false;
        if (first == second) return true;
        var vanillaTeam = first.getTeam();
        if (vanillaTeam != null && vanillaTeam == second.getTeam()) return true;
        if (!(first instanceof ServerPlayer firstPlayer)
                || !(second instanceof ServerPlayer secondPlayer)) return false;
        var server = firstPlayer.level().getServer();
        return server != null && AcademyTeamData.get(server).areTeammates(
                firstPlayer.getUUID(), secondPlayer.getUUID());
    }

    public static boolean areTeammates(Player first, Player second) {
        return areTeammates((Entity) first, second);
    }

    /**
     * Unified gameplay alliance check for ability targeting.
     *
     * <p>This preserves entity-specific alliance rules (including mental-control relations and
     * owned entities) while also admitting members of the self-service {@code /teams} system.</p>
     */
    public static boolean areAllied(Entity first, Entity second) {
        if (first == null || second == null) return false;
        if (areTeammates(first, second)) return true;
        return first.isAlliedTo(second);
    }
}
