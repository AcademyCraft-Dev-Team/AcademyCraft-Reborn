package org.academy.api.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityDataMpTest {
    @Test
    void categoriesWithoutAResourceDefaultToZeroMp() {
        var data = new AbilityData();
        assertEquals(0.0f, data.getCurrMP());
        assertEquals(0.0f, data.getMaxMP());
    }

    @Test
    void currentMpCanExceedItsNaturalRecoveryLimit() {
        var data = AbilityData.builder().currMP(12.0f).maxMP(10.0f).build();
        assertEquals(12.0f, data.getCurrMP());

        data.setMaxMP(4.0f);
        assertEquals(12.0f, data.getCurrMP());
        assertEquals(4.0f, data.getMaxMP());

        data.addMP(3.0f);
        assertEquals(15.0f, data.getCurrMP());
    }

    @Test
    void nonFiniteMpCannotEnterPersistentState() {
        var data = new AbilityData();
        data.setMaxMP(Float.NaN);
        data.setCurrMP(Float.POSITIVE_INFINITY);
        data.addMP(Float.NEGATIVE_INFINITY);
        assertEquals(0.0f, data.getCurrMP());
        assertEquals(0.0f, data.getMaxMP());
    }
}
