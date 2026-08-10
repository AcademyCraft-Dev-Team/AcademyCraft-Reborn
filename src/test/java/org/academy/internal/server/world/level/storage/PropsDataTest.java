package org.academy.internal.server.world.level.storage;

import org.academy.api.common.attribute.AbilityFactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropsDataTest {
    @Test
    void acquisitionStartsOnlyOnce() {
        var data = new PropsData();
        assertFalse(data.isStarted());
        assertTrue(data.start());
        assertTrue(data.isStarted());
        assertFalse(data.start());
    }

    @Test
    void oversizedLegacyValuesAreScaledProportionally() {
        var data = new PropsData();
        data.initialize(new double[]{2_000.0, 1_000.0, 500.0, 250.0, 250.0});

        assertEquals(2_000.0, data.total(), 1.0E-9);
        assertEquals(1_000.0, data.get(AbilityFactor.MUSCLE_STRENGTH), 1.0E-9);
        assertEquals(500.0, data.get(AbilityFactor.ENDURANCE), 1.0E-9);
    }

    @Test
    void locksVisitsAndMilestonesAreStable() {
        var data = new PropsData();
        assertTrue(data.setLocked(AbilityFactor.NEURAL_ACTIVITY, true));
        assertTrue(data.isLocked(AbilityFactor.NEURAL_ACTIVITY));
        assertFalse(data.setLocked(AbilityFactor.NEURAL_ACTIVITY, true));

        assertTrue(data.visitStructure("minecraft:overworld|minecraft:village|0,0"));
        assertFalse(data.visitStructure("minecraft:overworld|minecraft:village|0,0"));
        assertTrue(data.markMilestone(1));
        assertFalse(data.markMilestone(1));
    }

    @Test
    void invalidValuesAreSanitized() {
        var data = new PropsData();
        data.initialize(new double[]{Double.NaN, -1.0, Double.POSITIVE_INFINITY, 10.0, 20.0});
        assertEquals(30.0, data.total());
    }
}
