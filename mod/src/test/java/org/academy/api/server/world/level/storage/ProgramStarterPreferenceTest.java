package org.academy.api.server.world.level.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramStarterPreferenceTest {
    @Test
    void dismissalSurvivesWorldReloadAndIsScopedToThePlayerAndSave() {
        var gson = WorldData.createGson();
        var playerId = UUID.randomUUID();
        var otherPlayerId = UUID.randomUUID();
        var world = new WorldData();
        var player = new PlayerData();
        world.getPlayers().put(playerId, player);
        world.getPlayers().put(otherPlayerId, new PlayerData());

        player.dismissProgramStarter();
        assertTrue(player.isDirty());
        var reloaded = gson.fromJson(gson.toJson(world), WorldData.class);
        assertTrue(reloaded.getPlayers().get(playerId).isProgramStarterDismissed());
        assertFalse(reloaded.getPlayers().get(otherPlayerId).isProgramStarterDismissed());

        var otherWorld = new WorldData();
        otherWorld.getPlayers().put(playerId, new PlayerData());
        assertFalse(otherWorld.getPlayers().get(playerId).isProgramStarterDismissed());
        assertFalse(gson.fromJson("{}", PlayerData.class).isProgramStarterDismissed());
    }
}
