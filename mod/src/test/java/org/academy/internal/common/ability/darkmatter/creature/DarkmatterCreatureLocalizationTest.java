package org.academy.internal.common.ability.darkmatter.creature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterCreatureLocalizationTest {
    private static final Set<String> SCREEN_KEYS = Set.of(
            "screen.academy.darkmatter_creation.dismantle",
            "screen.academy.darkmatter_creation.dismantle_all",
            "screen.academy.darkmatter_creation.empty",
            "screen.academy.darkmatter_creation.error.additional",
            "screen.academy.darkmatter_creation.error.additional_phase",
            "screen.academy.darkmatter_creation.error.head",
            "screen.academy.darkmatter_creation.error.head_phase",
            "screen.academy.darkmatter_creation.error.investment",
            "screen.academy.darkmatter_creation.error.limbs",
            "screen.academy.darkmatter_creation.error.limbs_phase",
            "screen.academy.darkmatter_creation.error.module",
            "screen.academy.darkmatter_creation.error.module_budget",
            "screen.academy.darkmatter_creation.error.torso",
            "screen.academy.darkmatter_creation.error.torso_phase",
            "screen.academy.darkmatter_creation.investment",
            "screen.academy.darkmatter_creation.invalid",
            "screen.academy.darkmatter_creation.missing_description",
            "screen.academy.darkmatter_creation.missing_part",
            "screen.academy.darkmatter_creation.module_budget",
            "screen.academy.darkmatter_creation.module_entry",
            "screen.academy.darkmatter_creation.module_usage",
            "screen.academy.darkmatter_creation.name",
            "screen.academy.darkmatter_creation.part.additional",
            "screen.academy.darkmatter_creation.part.head",
            "screen.academy.darkmatter_creation.part.limbs",
            "screen.academy.darkmatter_creation.part.torso",
            "screen.academy.darkmatter_creation.phase_pool",
            "screen.academy.darkmatter_creation.phase_value",
            "screen.academy.darkmatter_creation.roster.distance",
            "screen.academy.darkmatter_creation.roster.row",
            "screen.academy.darkmatter_creation.roster.unloaded",
            "screen.academy.darkmatter_creation.save",
            "screen.academy.darkmatter_creation.stats",
            "screen.academy.darkmatter_creation.summon",
            "screen.academy.darkmatter_creation.tab.blueprint",
            "screen.academy.darkmatter_creation.tab.modules",
            "screen.academy.darkmatter_creation.tab.parts",
            "screen.academy.darkmatter_creation.tab.phase",
            "screen.academy.darkmatter_creation.tab.summoned",
            "screen.academy.darkmatter_creation.title",
            "screen.academy.darkmatter_creation.valid"
    );

    @Test
    void everyBuiltInPartModuleAndEditorFunctionHasEnglishAndChineseText() {
        for (var language : new String[]{"en_us", "zh_cn"}) {
            var translations = loadLanguage(language);
            var missing = new ArrayList<String>();
            for (var part : DarkmatterCreatureRegistries.parts()) {
                collectMissing(translations, part.translationKey(), missing);
                collectMissing(translations, part.descriptionTranslationKey(), missing);
            }
            for (var module : DarkmatterCreatureRegistries.modules()) {
                collectMissing(translations, module.translationKey(), missing);
                collectMissing(translations, module.descriptionTranslationKey(), missing);
            }
            SCREEN_KEYS.forEach(key -> collectMissing(translations, key, missing));
            assertTrue(missing.isEmpty(), language + " missing or blank: " + missing);
        }
    }

    private static JsonObject loadLanguage(String language) {
        var path = "/assets/academy/lang/" + language + ".json";
        var stream = DarkmatterCreatureLocalizationTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void collectMissing(JsonObject translations, String key,
                                       ArrayList<String> missing) {
        if (!translations.has(key) || translations.get(key).getAsString().isBlank()) {
            missing.add(key);
        }
    }
}
