package org.academy.api.common.ability.darkmatter;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;

/**
 * Server-authoritative access to a player's dark-matter phase and resource ledger.
 * Implementations apply resource and matching CP occupation mutations atomically.
 */
public interface DarkmatterResourceService {
    DarkmatterPhaseSnapshot getPhaseSnapshot(ServerPlayer player);

    DarkmatterResourceView getView(ServerPlayer player);

    float getBaseCapacity(ServerPlayer player);

    float getEffectiveCapacity(ServerPlayer player);

    boolean setAlphaPoints(ServerPlayer player, int points);

    boolean tuneAlphaPoints(ServerPlayer player, float deltaPoints);

    boolean create(ServerPlayer player, float requestedUnits, float cpPerUnit);

    boolean consume(ServerPlayer player, float requestedUnits, Skill consumer, int iterationTicks);

    float consumeUpTo(ServerPlayer player, float requestedUnits, Skill consumer, int iterationTicks);

    /**
     * Adds earned MP without a CP debt or the natural-recovery capacity clamp.
     */
    boolean creditEarnedMatter(ServerPlayer player, float units);

    boolean reserve(ServerPlayer player, float units, Skill consumer, int iterationTicks);

    float releaseReservation(ServerPlayer player, float units);

    void requestSync(ServerPlayer player);
}
