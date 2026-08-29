package org.academy.internal.server.pvp;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PvpCooldownDataTest {
    private static final int ONE_MINUTE_TICKS = 20 * 60;

    @Test
    void cooldownOnlyAdvancesForTheOnlineUuid() {
        var data = new PvpCooldownData();
        var playerId = UUID.randomUUID();
        var otherPlayerId = UUID.randomUUID();
        data.startOrRefresh(playerId, ONE_MINUTE_TICKS);

        data.tickOnline(otherPlayerId);
        assertEquals(ONE_MINUTE_TICKS, data.remainingTicks(playerId));

        data.tickOnline(playerId);
        assertEquals(ONE_MINUTE_TICKS - 1, data.remainingTicks(playerId));
    }

    @Test
    void repeatedCombatRefreshesTheFullCooldown() {
        var data = new PvpCooldownData();
        var playerId = UUID.randomUUID();
        data.startOrRefresh(playerId, ONE_MINUTE_TICKS);
        for (var tick = 0; tick < 40; tick++) data.tickOnline(playerId);

        data.startOrRefresh(playerId, ONE_MINUTE_TICKS);

        assertEquals(ONE_MINUTE_TICKS, data.remainingTicks(playerId));
    }

    @Test
    void remainingTicksSurviveSavedDataSerialization() {
        var data = new PvpCooldownData();
        var playerId = UUID.randomUUID();
        data.startOrRefresh(playerId, ONE_MINUTE_TICKS);
        data.tickOnline(playerId);
        var encoded = PvpCooldownData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();

        var decoded = PvpCooldownData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(ONE_MINUTE_TICKS - 1, decoded.remainingTicks(playerId));
    }
}
