package org.academy.internal.common.ability.aeromanip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeromanipSkillMigrationTest {
    @Test
    void everyReplacementCanReadItsLegacyConfiguration() {
        AeromanipSkillMigration.LEGACY_TO_REPLACEMENT.forEach((legacy, replacement) ->
                assertEquals(legacy,
                        AeromanipSkillMigration.legacyForReplacement(replacement).orElseThrow()));
    }

    @Test
    void unrelatedSkillIdsAreNotRewritten() {
        assertTrue(AeromanipSkillMigration.legacyForReplacement("airflow_jet").isEmpty());
        assertTrue(AeromanipSkillMigration.replacementForLegacy("flow_sense").isEmpty());
    }
}
