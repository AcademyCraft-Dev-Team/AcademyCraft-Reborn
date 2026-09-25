package org.academy.api.client.config;

import com.google.gson.Gson;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WingFlightConfigTest {
    private final Gson gson = new Gson();

    @Test
    void oldConfigsDefaultToFullMomentumForEveryWing() {
        for (var type : List.of(StormWing.Client.Config.class, BlackWing.Client.Config.class,
                WhiteWing.Client.Config.class, PlatinumWing.Client.Config.class)) {
            var config = gson.fromJson("{\"keyBindings\":{}}", type);
            assertEquals(1, config.getRemainingMomentum(), type.getName());
        }
    }

    @Test
    void savedSettingsRoundTripIncludingZeroAndIntermediateValues() {
        for (var value : new float[]{0, .01f, .37f, .5f, 1}) {
            var config = new WingFlightConfig();
            config.setRemainingMomentum(value);
            var loaded = gson.fromJson(gson.toJson(config), WingFlightConfig.class);
            assertEquals(value, loaded.getRemainingMomentum());
        }
    }
}
