package org.academy.internal.common.structure.upgrade.lifecycle;

import org.academy.internal.server.config.GenericConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureRestSettingsTest {
    @Test void missingSettingsFailClosedAndMalformedBudgetsStayBounded() {
        assertFalse(StructureRestSettings.from(null).enabled());
        var config = new GenericConfig.StructureRestorationSettings();
        config.restTicks = -1;
        config.checkIntervalTicks = Integer.MAX_VALUE;
        config.candidatesPerTick = Integer.MAX_VALUE;
        config.retryTicks = 0;
        var settings = StructureRestSettings.from(config);
        assertEquals(20, settings.restTicks());
        assertEquals(200, settings.checkIntervalTicks());
        assertEquals(32, settings.candidatesPerTick());
        assertEquals(20, settings.retryTicks());
    }
}
