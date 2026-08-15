package org.academy.internal.server.world.level.storage;

import org.academy.api.common.ability.AbilityLevel;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.skilldata.SkillData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldDataMigrationTest {
    private static final UUID PLAYER_ID = UUID.fromString("d96a465b-b8ca-4f14-a047-65289d9ae91c");

    @Test
    void migratesLegacy121IdentifiersAndPlayerState() {
        var json = """
                {
                  "players": {
                    "d96a465b-b8ca-4f14-a047-65289d9ae91c": {
                      "skills": ["arc_generate", "current_recharge", "brain_development_lv3"],
                      "skillData": {
                        "arc_generate": {"exp": 12.0},
                        "lightning_spear": {"exp": 42.0},
                        "academy:thunder_clap": {"exp": 30.0},
                        "spreading_blast": {"exp": 18.0}
                      },
                      "abilityCategory": "electromaster",
                      "level": 4,
                      "computingPower": 320.0,
                      "maxComputingPower": 550.0,
                      "computingPowerRecoverySpeed": 8.0
                    }
                  }
                }
                """;

        var worldData = WorldData.createGson().fromJson(json, WorldData.class);
        assertTrue(worldData.migrateLegacyData());

        var player = worldData.getPlayers().get(PLAYER_ID);
        assertNotNull(player);
        assertEquals("academy:electromaster", player.getAbilityCategory());
        assertEquals(AbilityLevel.LEVEL4, player.getCpData().getLevel());
        assertEquals(550.0f, player.getCpData().getMaxCP());
        assertEquals(320.0f, player.getCpData().getAvailableCP());

        var skills = player.getSkillDataMap();
        assertTrue(skills.containsKey("academy:arc_generate"));
        assertTrue(skills.containsKey("academy:current_recharge"));
        assertTrue(skills.containsKey("academy:parallel_thought_computation"));
        assertTrue(skills.containsKey("academy:thunder_lance"));
        assertTrue(skills.containsKey("academy:thunderclap"));
        assertTrue(skills.containsKey("academy:scatter_bomb"));
        assertEquals(31.5f, skills.get("academy:thunder_lance").getProficiency());
        assertFalse(skills.containsKey("arc_generate"));
        assertFalse(skills.containsKey("lightning_spear"));
        assertFalse(skills.containsKey("academy:thunder_clap"));
        assertFalse(skills.containsKey("spreading_blast"));
        assertTrue(player.isDirty());
    }

    @Test
    void keepsForeignNamespacedSkillIdsUntouched() {
        assertEquals("othermod:custom_skill", Player.canonicalizeSkillId("othermod:custom_skill"));
    }

    @Test
    void migratesRenamedCurrentSkillIdentifiers() {
        assertEquals("academy:current_recharge", Player.canonicalizeSkillId("academy:pulse_charge"));
        assertEquals("academy:vector_deviation", Player.canonicalizeSkillId("academy:vector_reduction"));
        assertEquals("academy:piercing_teleportation", Player.canonicalizeSkillId("academy:cut_through"));
        assertEquals("academy:parallel_thought_computation",
                Player.canonicalizeSkillId("academy:level0_passive_lv3"));
    }

    @Test
    void persistsTheGrownCpMaximumAndItsMigrationMarker() {
        var worldData = new WorldData();
        var player = new Player();
        player.getCpData().setMaxCP(640.0f);
        player.getCpData().setAvailableCP(512.0f);
        player.setMaxCpInitialized(true);
        worldData.getPlayers().put(PLAYER_ID, player);

        var gson = WorldData.createGson();
        var restored = gson.fromJson(gson.toJson(worldData), WorldData.class)
                .getPlayers().get(PLAYER_ID);

        assertNotNull(restored);
        assertEquals(640.0f, restored.getCpData().getMaxCP());
        assertEquals(512.0f, restored.getCpData().getAvailableCP());
        assertTrue(restored.isMaxCpInitialized());
    }

    @Test
    void persistsServerAuthoritativeAbilityProgramBooks() {
        var worldData = new WorldData();
        var player = new Player();
        player.setAbilityProgramBook("academy:accelerator", "encoded-program-book");
        worldData.getPlayers().put(PLAYER_ID, player);

        var gson = WorldData.createGson();
        var restored = gson.fromJson(gson.toJson(worldData), WorldData.class)
                .getPlayers().get(PLAYER_ID);

        assertNotNull(restored);
        assertEquals("encoded-program-book",
                restored.getAbilityProgramBook("academy:accelerator"));
    }

    @Test
    void removesRetiredHellFlareDataAndOccupation() {
        var json = """
                {
                  "players": {
                    "d96a465b-b8ca-4f14-a047-65289d9ae91c": {
                      "skills": ["hell_flare"],
                      "skillData": {
                        "academy:hell_flare": {"exp": 600.0, "enabled": true}
                      },
                      "cpOccupations": [
                        {
                          "amount": 10.0,
                          "iterationTicks": 20,
                          "skillId": "hell_flare",
                          "isPermanent": true
                        }
                      ]
                    }
                  }
                }
                """;

        var worldData = WorldData.createGson().fromJson(json, WorldData.class);
        assertTrue(worldData.migrateLegacyData());

        var player = worldData.getPlayers().get(PLAYER_ID);
        assertNotNull(player);
        assertFalse(player.getSkillDataMap().containsKey("academy:hell_flare"));
        assertTrue(player.getCpOccupations().isEmpty());
        assertTrue(player.isDirty());
    }

    @Test
    void readsLegacyReflectionFilterCustomData() {
        SkillDataSerializer.registerType(ReflectionFilter.Data.ID, ReflectionFilter.Data.class);
        var json = """
                {
                  "_type": "org.academy.internal.common.ability.builtin.accelerator.skills.ReflectionFilter$Data",
                  "exp": 27.0,
                  "mode": "POSITIVE_FILTER",
                  "whitelist": ["minecraft:speed"],
                  "blacklist": ["minecraft:poison"]
                }
                """;

        var data = WorldData.createGson().fromJson(json, SkillData.class);
        assertInstanceOf(ReflectionFilter.Data.class, data);
        var filterData = (ReflectionFilter.Data) data;
        assertTrue(filterData.hasLegacyProgress());
        filterData.migrateLegacyProgress(3);
        assertEquals(20.25f, filterData.getProficiency());
        assertEquals(ReflectionFilter.Mode.POSITIVE_FILTER, filterData.getMode());
        assertEquals(List.of("minecraft:speed"), filterData.getWhitelist());
        assertEquals(List.of("minecraft:poison"), filterData.getBlacklist());
    }
}
