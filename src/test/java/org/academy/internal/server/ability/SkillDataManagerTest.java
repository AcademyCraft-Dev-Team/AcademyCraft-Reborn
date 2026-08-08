package org.academy.internal.server.ability;

import org.academy.api.common.ability.SkillScope;
import org.academy.api.common.ability.SkillActivity;
import org.academy.api.common.ability.ProficiencyEvent;
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

    @Test
    void effectiveActivityWinsSameTickDeduplication() {
        assertEquals(SkillActivity.ACTIVE,
                SkillDataManager.strongestActivity(SkillActivity.ACTIVE, SkillActivity.ACTIVE));
        assertEquals(SkillActivity.EFFECTIVE,
                SkillDataManager.strongestActivity(SkillActivity.ACTIVE, SkillActivity.EFFECTIVE));
        assertEquals(SkillActivity.EFFECTIVE,
                SkillDataManager.strongestActivity(SkillActivity.EFFECTIVE, SkillActivity.ACTIVE));
    }

    @Test
    void continuousEventsAreMutuallyExclusiveAndPassiveTakesItsOwnRate() {
        assertEquals(ProficiencyEvent.ACTIVE_TICK,
                SkillDataManager.resolveContinuousEvent(false, SkillActivity.ACTIVE));
        assertEquals(ProficiencyEvent.EFFECTIVE_TICK,
                SkillDataManager.resolveContinuousEvent(false, SkillActivity.EFFECTIVE));
        assertEquals(ProficiencyEvent.PASSIVE_TICK,
                SkillDataManager.resolveContinuousEvent(true, SkillActivity.EFFECTIVE));
        assertEquals(null, SkillDataManager.resolveContinuousEvent(false, null));
    }
}
