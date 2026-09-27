package org.academy.internal.client.ability.teleport;

import com.google.gson.Gson;
import org.academy.internal.common.ability.teleport.skills.lv2.PiercingTeleportation;
import org.academy.internal.common.ability.teleport.skills.lv2.SelfTeleport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportDistanceConfigTest {
    @Test
    void distancesAreIndependentAndSurviveSerialization() {
        var self = new SelfTeleport.Client.Config();
        var piercing = new PiercingTeleportation.Client.Config();
        self.setDefaultDistance(12);
        piercing.setDefaultDistance(57);
        var gson = new Gson();
        assertEquals(12, gson.fromJson(gson.toJson(self), SelfTeleport.Client.Config.class).getDefaultDistance());
        assertEquals(57, gson.fromJson(gson.toJson(piercing), PiercingTeleportation.Client.Config.class).getDefaultDistance());
        assertEquals(40, gson.fromJson("{}", SelfTeleport.Client.Config.class).getDefaultDistance());
    }

    @Test
    void malformedAndOutOfRangeDistancesCannotEscapeServerLimits() {
        var config = new SelfTeleport.Client.Config();
        config.setDefaultDistance(Double.NaN);
        assertEquals(40, config.getDefaultDistance());
        config.setDefaultDistance(500);
        assertEquals(64, config.getDefaultDistance());
        config.setDefaultDistance(-8);
        assertEquals(0, config.getDefaultDistance());
    }
}
