package org.academy.internal.common.ability.accelerator.reflection.compat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorCompatProfileTest {
    @Test
    void codecLoadsExplicitThirdPartyHitscanProfile() {
        var json = JsonParser.parseString("""
                {
                  "damage_type": ["thirdparty:particle_lance"],
                  "direct_entity": ["thirdparty:beam_anchor"],
                  "shape": "hitscan",
                  "direction": "source_position",
                  "range": 110.0,
                  "radius": 0.75,
                  "piercing": true,
                  "continuous": true,
                  "safe_motion_redirect": false,
                  "visual": "arc",
                  "block_policy": "break_allowed",
                  "priority": 20
                }
                """);

        var profile = VectorCompatProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(java.util.List.of("thirdparty:particle_lance"), profile.damageTypes());
        assertEquals(java.util.List.of("thirdparty:beam_anchor"), profile.directEntityTypes());
        assertEquals(VectorCompatProfile.DirectionMode.SOURCE_POSITION, profile.direction());
        assertEquals(110.0, profile.range());
        assertEquals(0.75, profile.radius());
        assertTrue(profile.piercing());
        assertTrue(profile.continuous());
        assertEquals(VectorVisualStyle.ARC, profile.visual());
        assertEquals(VectorBlockPolicy.BREAK_ALLOWED, profile.blockPolicy());
        assertEquals(VectorExecutionPolicy.HARD_MAXIMUM_TARGETS, profile.executionPolicy().maximumTargets());
    }

    @Test
    void emptySelectorCannotBecomeGlobalProfile() {
        var profile = VectorCompatProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{}")
        ).getOrThrow();

        assertTrue(profile.damageTypes().isEmpty());
        assertTrue(profile.directEntityTypes().isEmpty());
        assertFalse(profile.deny());
        assertEquals(VectorExecutionPolicy.DEFAULT_MAXIMUM_RANGE, profile.range());
    }

    @Test
    void unsafeProfileValuesAreClamped() {
        var profile = new VectorCompatProfile(
                false,
                java.util.List.of(" ThirdParty:Beam ", "thirdparty:beam"),
                java.util.List.of(),
                null,
                null,
                Double.POSITIVE_INFINITY,
                100.0,
                false,
                false,
                false,
                null,
                null,
                0
        );

        assertEquals(java.util.List.of("thirdparty:beam"), profile.damageTypes());
        assertEquals(VectorExecutionPolicy.DEFAULT_MAXIMUM_RANGE, profile.range());
        assertEquals(8.0, profile.radius());
        assertEquals(VectorBlockPolicy.CLIP_NO_BREAK, profile.blockPolicy());
    }
}
