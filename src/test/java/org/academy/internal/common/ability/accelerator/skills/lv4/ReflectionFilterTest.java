package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.google.gson.Gson;
import net.minecraft.world.effect.MobEffectCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionFilterTest {
    private static final Gson GSON = new Gson();

    @Test
    void appliesAllThreeReferenceModes() {
        var data = data("REFLECT_ALL", "[]", "[]");
        assertFalse(accept(data, "minecraft:speed", MobEffectCategory.BENEFICIAL));
        assertFalse(accept(data, "minecraft:poison", MobEffectCategory.HARMFUL));

        data = data("POSITIVE_FILTER", "[]", "[]");
        assertTrue(accept(data, "minecraft:speed", MobEffectCategory.BENEFICIAL));
        assertFalse(accept(data, "minecraft:poison", MobEffectCategory.HARMFUL));

        data = data("NEUTRAL_FILTER", "[]", "[]");
        assertTrue(accept(data, "minecraft:speed", MobEffectCategory.BENEFICIAL));
        assertFalse(accept(data, "minecraft:poison", MobEffectCategory.HARMFUL));
    }

    @Test
    void blacklistWinsAndWhitelistOverridesTheMode() {
        var data = data("REFLECT_ALL", "[\"minecraft:speed\",\"minecraft:poison\"]",
                "[\"minecraft:poison\"]");

        assertTrue(accept(data, "minecraft:speed", MobEffectCategory.BENEFICIAL));
        assertFalse(accept(data, "minecraft:poison", MobEffectCategory.HARMFUL));
    }

    private static ReflectionFilter.Data data(String mode, String whitelist, String blacklist) {
        return GSON.fromJson("{\"mode\":\"" + mode + "\",\"whitelist\":" + whitelist
                + ",\"blacklist\":" + blacklist + "}", ReflectionFilter.Data.class);
    }

    private static boolean accept(ReflectionFilter.Data data, String effectId, MobEffectCategory category) {
        return ReflectionFilter.shouldAcceptNormalizedEffect(data, effectId, category);
    }
}
