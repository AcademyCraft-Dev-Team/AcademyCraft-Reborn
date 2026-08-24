package org.academy.internal.common.world.level.block;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MachineBlockMiningPropertiesTest {
    private static final List<String> MACHINE_BLOCKS = List.of(
            "ability_developer",
            "cat_engine",
            "omni_crafting_table",
            "solar_gen",
            "wind_gen_base",
            "wind_gen_pillar",
            "wind_gen_top",
            "wireless_node"
    );

    @Test
    void everyMachineIsPickaxeMineableAndDropsItself() throws Exception {
        var pickaxeTag = resourceJson("/data/minecraft/tags/block/mineable/pickaxe.json");
        var values = pickaxeTag.getAsJsonArray("values");

        for (var block : MACHINE_BLOCKS) {
            assertEquals(true, values.contains(JsonParser.parseString("\"academy:" + block + "\"")));

            var lootTable = resourceJson("/data/academy/loot_table/blocks/" + block + ".json");
            var entry = lootTable.getAsJsonArray("pools")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("entries")
                    .get(0).getAsJsonObject();
            assertEquals("academy:" + block, entry.get("name").getAsString());
        }
    }

    private static com.google.gson.JsonObject resourceJson(String path) throws Exception {
        var stream = MachineBlockMiningPropertiesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
