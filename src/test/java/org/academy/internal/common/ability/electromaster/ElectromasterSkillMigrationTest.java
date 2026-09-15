package org.academy.internal.common.ability.electromaster;

import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ElectromasterSkillMigrationTest {
    @Test void mergesMaximumAndRemovesLegacyShield() {
        var skills = new HashMap<String, SkillData>();
        var sand = new CommonSkillData(); sand.setProficiency(1200); sand.setEnabled(false);
        var shield = new CommonSkillData(); shield.setProficiency(2400);
        skills.put(ElectromasterSkillMigration.IRON_SAND, sand);
        skills.put(ElectromasterSkillMigration.SHIELD, shield);
        assertTrue(ElectromasterSkillMigration.merge(skills));
        assertEquals(2400, sand.getProficiency());
        assertTrue(sand.isEnabled());
        assertFalse(skills.containsKey(ElectromasterSkillMigration.SHIELD));
        ElectromasterSkillMigration.merge(skills);
        assertEquals(2400, sand.getProficiency());
    }
    @Test void shieldOnlyIsPreservedAndEmptySavesDoNotLearnSkills() {
        var skills = new HashMap<String, SkillData>();
        assertFalse(ElectromasterSkillMigration.merge(skills));
        var shield = new CommonSkillData(); shield.setProficiency(3000);
        skills.put(ElectromasterSkillMigration.SHIELD, shield);
        ElectromasterSkillMigration.merge(skills);
        assertEquals(3000, skills.get(ElectromasterSkillMigration.IRON_SAND).getProficiency());
        assertEquals(1, skills.size());
    }
}
