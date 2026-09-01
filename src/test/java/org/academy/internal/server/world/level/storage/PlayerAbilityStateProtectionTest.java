package org.academy.internal.server.world.level.storage;

import org.academy.api.common.data.AbilityData;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAbilityStateProtectionTest {
    @Test
    void publicCollectionsCannotRemoveSkillsOrInjectOccupations() {
        var player = WorldData.createGson().fromJson("""
                {
                  "skillData": {
                    "academy:vector_reflection": {"proficiency": 1000.0}
                  }
                }
                """, Player.class);

        assertThrows(UnsupportedOperationException.class, () ->
                player.getSkillDataMap().remove("academy:vector_reflection"));
        assertThrows(UnsupportedOperationException.class, () ->
                player.getSkillDataMap().put("foreign:skill", new CommonSkillData()));
        assertThrows(UnsupportedOperationException.class, () ->
                player.getCpOccupations().add(new AbilityData.CpOccupationData(
                        100.0f,
                        20,
                        "foreign:skill",
                        false
                )));

        assertTrue(player.isSkillLearned("academy:vector_reflection"));
        assertTrue(player.getCpOccupations().isEmpty());
    }

    @Test
    void foreignReflectionCannotRewriteCategoryOrPersistentSwitch()
            throws ReflectiveOperationException {
        var player = new Player();

        Player.class.getMethod("setAbilityCategory", String.class)
                .invoke(player, "academy:level0");
        Player.class.getMethod("setPersistedSkillEnabled", String.class, boolean.class)
                .invoke(player, "academy:vector_reflection", false);

        assertNull(player.getAbilityCategory());
        assertTrue(player.getPersistedSkillEnabled("academy:vector_reflection").isEmpty());
    }
}
