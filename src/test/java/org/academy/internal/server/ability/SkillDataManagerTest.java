package org.academy.internal.server.ability;

import org.academy.api.common.ability.SkillScope;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDataManagerTest {
    @Test
    void categoryChangePreservesCommonSkillsAndRemovesOtherEntries() {
        var skillData = new HashMap<String, SkillData>();
        skillData.put("academy:category_skill", new CommonSkillData());
        skillData.put("academy:common_skill", new CommonSkillData());
        skillData.put("academy:unknown_skill", new CommonSkillData());

        var removed = SkillDataManager.removeCategorySkills(skillData, skillId -> switch (skillId) {
            case "academy:category_skill" -> SkillScope.CATEGORY;
            case "academy:common_skill" -> SkillScope.COMMON;
            default -> null;
        });

        assertEquals(2, removed);
        assertFalse(skillData.containsKey("academy:category_skill"));
        assertFalse(skillData.containsKey("academy:unknown_skill"));
        assertTrue(skillData.containsKey("academy:common_skill"));
    }
}
