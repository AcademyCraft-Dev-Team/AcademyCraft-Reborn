package org.academy.internal.common.ability.electromaster;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.ability.AreaEffectTargets;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SkyStrikeImpactRadiusTest {
    @Test
    void serverRadiusMatchesBothGraphImpactAndAttachmentDefaults() throws Exception {
        for (var profile : SkyStrikeProfile.values()) {
            String asset = profile == SkyStrikeProfile.THUNDERCLAP ? "thunderclap" : "storm";
            assertEquals(profile == SkyStrikeProfile.THUNDERCLAP ? 14f : 6f, profile.ringEndRadius());
            try (var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/sky_strike_" + asset + ".json")) {
                assertNotNull(stream);
                var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                int matched = 0;
                for (var context : root.getAsJsonArray("contexts")) {
                    for (var block : context.getAsJsonObject().getAsJsonArray("blocks")) {
                        var properties = block.getAsJsonObject().getAsJsonObject("properties");
                        if (!properties.has("ground_radius")) continue;
                        assertEquals(profile.ringEndRadius(), properties.get("ground_radius").getAsFloat());
                        matched++;
                    }
                }
                assertEquals(2, matched);
            }
        }
    }

    @Test
    void impactIncludesTheCircleEdgeButNotOutsideOrSquareCorners() {
        var origin = new Vec3(17, 64, -20);
        for (var profile : SkyStrikeProfile.values()) {
            double radius = profile.ringEndRadius();
            assertTrue(AreaEffectTargets.contains(origin, origin.add(radius - 0.01, 0, 0), radius));
            assertTrue(AreaEffectTargets.contains(origin, origin.add(radius, 0, 0), radius));
            assertFalse(AreaEffectTargets.contains(origin, origin.add(radius + 0.01, 0, 0), radius));
            assertFalse(AreaEffectTargets.contains(origin, origin.add(radius * 0.8, 0, radius * 0.8), radius));
            assertFalse(AreaEffectTargets.contains(origin, origin.add(0, radius + 0.01, 0), radius));
        }
        assertFalse(AreaEffectTargets.contains(origin, origin, Double.NaN));
        assertFalse(AreaEffectTargets.contains(origin, new Vec3(Double.NaN, 0, 0), 14));
        assertFalse(AreaEffectTargets.contains(origin, origin, -1));
    }
}
