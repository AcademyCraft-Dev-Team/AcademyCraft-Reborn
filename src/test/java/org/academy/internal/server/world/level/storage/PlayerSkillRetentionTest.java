package org.academy.internal.server.world.level.storage;

import org.academy.internal.common.skilldata.CommonSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerSkillRetentionTest {
    @Test
    void restoresArchivedCategorySkillProficiencyWhenSkillIsLearnedAgain() {
        var player = new Player();
        var removedSkill = new CommonSkillData();
        removedSkill.setProficiency(2345.0f);

        player.retainSkillProficiency("academy:test_category_skill", removedSkill);

        var relearnedSkill = new CommonSkillData();
        player.restoreRetainedSkillProficiency("academy:test_category_skill", relearnedSkill);

        assertEquals(2345.0f, relearnedSkill.getProficiency());
    }

    @Test
    void neverLowersNewerProficiencyWhenRestoringArchivedData() {
        var player = new Player();
        var removedSkill = new CommonSkillData();
        removedSkill.setProficiency(1000.0f);
        player.retainSkillProficiency("academy:test_category_skill", removedSkill);

        var relearnedSkill = new CommonSkillData();
        relearnedSkill.setProficiency(2000.0f);
        player.restoreRetainedSkillProficiency("academy:test_category_skill", relearnedSkill);

        assertEquals(2000.0f, relearnedSkill.getProficiency());
    }

    @Test
    void challengeCpBonusIsCappedAtTwoHundred() {
        var player = new Player();

        for (var index = 0; index < 50; index++) {
            player.addChallengeCpBonus(5.0f);
        }

        assertEquals(200.0f, player.getChallengeCpBonus());
    }
}
