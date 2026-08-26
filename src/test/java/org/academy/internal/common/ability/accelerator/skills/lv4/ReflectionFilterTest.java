package org.academy.internal.common.ability.accelerator.skills.lv4;

import com.google.gson.Gson;
import net.minecraft.world.effect.MobEffectCategory;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionFilterTest {
    private static final Gson GSON = new Gson();

    private static ReflectionFilter.Data data(String mode, String whitelist, String blacklist) {
        return GSON.fromJson("{\"mode\":\"" + mode + "\",\"whitelist\":" + whitelist
                + ",\"blacklist\":" + blacklist + "}", ReflectionFilter.Data.class);
    }

    private static boolean accept(ReflectionFilter.Data data, String effectId, MobEffectCategory category) {
        return ReflectionFilter.shouldAcceptNormalizedEffect(data, effectId, category);
    }

    @Test
    void opensOnEqualByDefault() {
        assertEquals(java.util.Set.of(GLFW.GLFW_KEY_EQUAL),
                ReflectionFilter.defaultOpenKey().keys());
    }

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

    @Test
    void preservesForcedMovementProtectionInCopies() {
        var data = GSON.fromJson("{\"forcedMovementProtection\":true}",
                ReflectionFilter.Data.class);

        assertTrue(data.copy().isForcedMovementProtectionEnabled());
    }

    @Test
    void detectsOnlyEffectiveConfigurationChanges() {
        var original = data("POSITIVE_FILTER", "[\"minecraft:speed\"]", "[]");
        var same = original.copy();
        var changedList = data("POSITIVE_FILTER", "[]", "[\"minecraft:speed\"]");
        var changedMode = data("NEUTRAL_FILTER", "[\"minecraft:speed\"]", "[]");

        assertTrue(ReflectionFilter.hasSameConfiguration(original, same));
        assertFalse(ReflectionFilter.hasSameConfiguration(original, changedList));
        assertFalse(ReflectionFilter.hasSameConfiguration(original, changedMode));
    }
}
