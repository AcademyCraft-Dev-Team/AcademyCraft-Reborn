package org.academy.internal.common.ability.electromaster;

import com.google.gson.Gson;
import org.academy.internal.common.ability.electromaster.skills.lv4.LightningStorm;
import org.academy.internal.common.ability.electromaster.skills.lv5.Thunderclap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyStrikeClientConfigTest {
    private static final Gson GSON = new Gson();

    @Test
    void visualFeedbackDefaultsToEnabledAndClamps() {
        var storm = new LightningStorm.Client.Config();
        var thunderclap = new Thunderclap.Client.Config();

        assertEquals(1.0f, storm.getFlashIntensity());
        assertEquals(1.0f, storm.getShakeIntensity());
        assertEquals(1.0f, thunderclap.getFlashIntensity());
        assertEquals(1.0f, thunderclap.getShakeIntensity());

        storm.setFlashIntensity(-1.0f);
        storm.setShakeIntensity(2.0f);
        thunderclap.setFlashIntensity(Float.NaN);
        thunderclap.setShakeIntensity(0.5f);

        assertEquals(0.0f, storm.getFlashIntensity());
        assertEquals(1.0f, storm.getShakeIntensity());
        assertEquals(1.0f, thunderclap.getFlashIntensity());
        assertEquals(0.5f, thunderclap.getShakeIntensity());
    }

    @Test
    void legacyConfigWithoutVisualFieldsKeepsEnabledDefaults() {
        var storm = GSON.fromJson("{}", LightningStorm.Client.Config.class);
        var thunderclap = GSON.fromJson("{}", Thunderclap.Client.Config.class);

        assertEquals(1.0f, storm.getFlashIntensity());
        assertEquals(1.0f, storm.getShakeIntensity());
        assertEquals(1.0f, thunderclap.getFlashIntensity());
        assertEquals(1.0f, thunderclap.getShakeIntensity());
    }
}
