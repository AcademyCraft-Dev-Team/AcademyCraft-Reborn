package org.academy.internal.common.skilldata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDataProficiencyTest {
    @Test
    void proficiencyIsClampedToCanonicalRange() {
        var data = new CommonSkillData();

        data.setProficiency(-1.0f);
        assertEquals(0.0f, data.getProficiency());
        data.setProficiency(3001.0f);
        assertEquals(3000.0f, data.getProficiency());
        assertTrue(data.isMaxProficiency());
        data.setProficiency(Float.NaN);
        assertEquals(0.0f, data.getProficiency());
        assertFalse(data.isMaxProficiency());
    }

    @Test
    void legacyProgressUsesTheExactProportionalMigrationAndIsIdempotent() {
        var data = new CommonSkillData();
        data.markLegacyProgress(500.0f, 1000, 2);

        data.migrateLegacyProgress(3);
        assertEquals(1875.0f, data.getProficiency());
        assertFalse(data.hasLegacyProgress());

        data.migrateLegacyProgress(3);
        assertEquals(1875.0f, data.getProficiency());
    }

    @Test
    void invalidLegacyMaxExpFallsBackToOneThousand() {
        var data = new CommonSkillData();
        data.markLegacyProgress(500.0f, 0, 0);

        data.migrateLegacyProgress(3);

        assertEquals(375.0f, data.getProficiency());
    }
}
