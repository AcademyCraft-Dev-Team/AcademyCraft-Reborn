package org.academy.internal.client.ability.electromaster.mine;

import com.google.gson.Gson;
import org.academy.internal.common.ability.electromaster.skills.lv3.MineDetect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineDetectConfigTest {
    private final Gson gson = new Gson();

    @Test
    void legacyConfigKeepsTheFullRangeAtEachMilestone() {
        var config = gson.fromJson("{}", MineDetect.Client.Config.class);
        assertEquals(80, config.getScanRadius());
        assertEquals(64, config.getEffectiveScanRadius(0));
        assertEquals(64, config.getEffectiveScanRadius(1));
        assertEquals(80, config.getEffectiveScanRadius(2));
    }

    @Test
    void selectedRadiusSurvivesReloadAndNeverExceedsProficiencyLimit() {
        var config = new MineDetect.Client.Config();
        config.setScanRadius(23);
        var reloaded = gson.fromJson(gson.toJson(config), MineDetect.Client.Config.class);
        assertEquals(23, reloaded.getEffectiveScanRadius(0));
        assertEquals(23, reloaded.getEffectiveScanRadius(3));
        reloaded.setScanRadius(76);
        assertEquals(64, reloaded.getEffectiveScanRadius(1));
        assertEquals(76, reloaded.getEffectiveScanRadius(2));
    }

    @Test
    void bothLoadedAndUpdatedValuesStayWithinSupportedScanBounds() {
        var config = gson.fromJson("{\"scanRadius\":-200}", MineDetect.Client.Config.class);
        assertEquals(8, config.getScanRadius());
        config = gson.fromJson("{\"scanRadius\":2147483647}", MineDetect.Client.Config.class);
        assertEquals(80, config.getScanRadius());
        config.setScanRadius(Integer.MIN_VALUE);
        assertEquals(8, config.getScanRadius());
        config.setScanRadius(Integer.MAX_VALUE);
        assertEquals(80, config.getScanRadius());
    }
}
