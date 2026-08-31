package org.academy.internal.common.world.item;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterToolLootTagTest {
    @Test
    void darkmatterToolQualifiesForVanillaAmethystClusterFortuneLoot() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/minecraft/tags/item/cluster_max_harvestables.json")) {
            assertNotNull(stream);
            var tag = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(tag.get("replace").getAsBoolean());
            assertTrue(tag.getAsJsonArray("values").contains(
                    JsonParser.parseString("\"academy:darkmatter_tool\"")));
        }
    }
}
