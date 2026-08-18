package org.academy.api.common.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProficiencyCoverageTest {
    @Test
    void languageFilesHaveMatchingNaturallySortedKeys() {
        var englishKeys = new ArrayList<>(loadLanguage("en_us").keySet());
        var chineseKeys = new ArrayList<>(loadLanguage("zh_cn").keySet());

        assertEquals(englishKeys, chineseKeys, "language files must contain keys in the same order");
        var sortedKeys = new ArrayList<>(englishKeys);
        sortedKeys.sort(String::compareTo);
        assertEquals(sortedKeys, englishKeys, "language keys must use natural lexicographic order");
    }

    @Test
    void everyAbilityCategoryHasEnglishAndChineseNames() {
        for (var language : new String[]{"en_us", "zh_cn"}) {
            var translations = loadLanguage(language);
            for (var category : new String[]{
                    "accelerator", "aeromanip", "darkmatter", "electromaster",
                    "level0", "meltdowner", "mentalout", "teleport"
            }) {
                var key = "ability_category.academy." + category;
                assertTrue(translations.has(key), language + " missing " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), language + " blank " + key);
            }
        }
    }

    @Test
    void allNonCommonSkillsDeclareAProficiencyPlan() {
        assertEquals(85, SkillProficiencyProfiles.declaredSkillPaths().size());
        for (var path : SkillProficiencyProfiles.declaredSkillPaths()) {
            var id = "academy:" + path;
            assertTrue(SkillProficiencyProfiles.isDeclared(id), path);
            var profile = SkillProficiencyProfiles.forSkill(id);
            assertTrue(profile != SkillProficiencyProfile.NONE
                            || SkillProficiencyProfiles.customProfileReason(id) != null,
                    path + " needs a scalar profile or an explicit custom implementation reason");
        }
    }

    @Test
    void everyDeclaredSkillHasThreeLocalizedMilestoneDescriptions() {
        for (var language : new String[]{"en_us", "zh_cn"}) {
            var translations = loadLanguage(language);
            for (var path : SkillProficiencyProfiles.declaredSkillPaths()) {
                for (var threshold : new int[]{1000, 2000, 3000}) {
                    var key = "skill.academy." + path + ".proficiency." + threshold;
                    assertTrue(translations.has(key), language + " missing " + key);
                    var description = translations.get(key).getAsString();
                    assertTrue(!description.isBlank(), language + " blank " + key);
                    if (language.equals("en_us")) {
                        assertFalse(description.matches(".*unlocks its (first|second|final) proficiency enhancement.*"),
                                language + " placeholder " + key);
                    }
                }
            }
        }
    }

    @Test
    void areaTeleportUsesOneLocalizedSkillAndFiveUnifiedBindings() {
        assertTrue(SkillProficiencyProfiles.declaredSkillPaths().contains("area_teleport_select"));
        assertFalse(SkillProficiencyProfiles.declaredSkillPaths().contains("area_teleport_setup"));
        assertFalse(SkillProficiencyProfiles.declaredSkillPaths().contains("area_teleport_start"));
        for (var language : new String[]{"en_us", "zh_cn"}) {
            var translations = loadLanguage(language);
            assertTrue(translations.has("skill.academy.area_teleport_select"));
            assertFalse(translations.has("skill.academy.area_teleport_setup"));
            assertFalse(translations.has("skill.academy.area_teleport_start"));
            for (var action : new String[]{"mark", "run", "setup", "swap", "transform"}) {
                assertTrue(translations.has("key.academy.area_teleport_select_" + action),
                        language + " missing unified area teleport binding " + action);
            }
        }
    }

    @Test
    void knownUnimplementedMilestonesAreExplicitlyMarked() {
        var keys = new String[]{
                "skill.academy.atmospheric_dominion.proficiency.3000",
                "skill.academy.magnet_manipulation.proficiency.3000",
                "skill.academy.mine_detect.proficiency.3000",
                "skill.academy.current_recharge.proficiency.3000"
        };
        var chinese = loadLanguage("zh_cn");
        var english = loadLanguage("en_us");
        for (var key : keys) {
            assertTrue(chinese.get(key).getAsString().contains("（暂未完成）"), "zh_cn marker missing: " + key);
            assertTrue(english.get(key).getAsString().contains("(Not yet implemented)"),
                    "en_us marker missing: " + key);
        }
    }

    @Test
    void bothVectorDefensesUseTenTenTenFiveIterationSequence() {
        for (var path : new String[]{"vector_reflection", "vector_deviation"}) {
            var profile = SkillProficiencyProfiles.forSkill("academy:" + path);
            assertEquals(10, profile.resolveIterationTicks(0, 40), path);
            assertEquals(10, profile.resolveIterationTicks(1, 40), path);
            assertEquals(10, profile.resolveIterationTicks(2, 40), path);
            assertEquals(5, profile.resolveIterationTicks(3, 40), path);
        }
    }

    private static JsonObject loadLanguage(String language) {
        var path = "/assets/academy/lang/" + language + ".json";
        var stream = SkillProficiencyCoverageTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
