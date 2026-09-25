package org.academy.internal.server.ability;

import org.academy.api.common.data.AbilityData;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeCpRecoveryTest {
    private static Player grownPlayer(float maximum, float applied) {
        var player = new Player();
        player.getCpData().setMaxCP(maximum);
        player.getCpData().setAvailableCP(maximum);
        player.setAppliedCommonSkillMaxCpBonus(applied);
        player.setMaxCpInitialized(true);
        return player;
    }

    @Test
    void repairsAlreadyClampedSaveWithoutRegrantingItsRecordedBonuses() {
        var player = grownPlayer(400, 500);
        player.getCpData().setAvailableCP(350);
        assertTrue(PlayerCPManager.updateAcademyMaxCp(player, 500));
        assertTrue(PlayerCPManager.restoreAcademyMaxCp(player, null));
        assertEquals(600, player.getCpData().getMaxCP());
        assertEquals(550, player.getCpData().getAvailableCP());
        assertEquals(500, player.getAppliedCommonSkillMaxCpBonus());
        assertFalse(PlayerCPManager.updateAcademyMaxCp(player, 500));
        assertFalse(PlayerCPManager.restoreAcademyMaxCp(player, null));
    }

    @Test
    void retainsLegacyExtraGrowthAcrossCategoryWritesAndServerRestart() {
        var player = grownPlayer(740, 500);
        PlayerCPManager.updateAcademyMaxCp(player, 500);
        player.getCpData().setMaxCP(400);
        player.getCpData().setAvailableCP(370);
        var world = new WorldData();
        var id = UUID.randomUUID();
        world.getPlayers().put(id, player);
        var gson = WorldData.createGson();
        var restored = gson.fromJson(gson.toJson(world), WorldData.class).getPlayers().get(id);
        assertEquals(740, restored.getAcademyMaxCp());
        PlayerCPManager.restoreAcademyMaxCp(restored, null);
        assertEquals(740, restored.getCpData().getMaxCP());
        assertEquals(710, restored.getCpData().getAvailableCP());
    }

    @Test
    void recordsGrowthEvenWhenCategoryRepeatedlyOverwritesMutableMaximum() {
        var player = grownPlayer(600, 500);
        PlayerCPManager.updateAcademyMaxCp(player, 500);
        player.getCpData().setMaxCP(400);
        PlayerCPManager.updateAcademyMaxCp(player, 520);
        PlayerCPManager.restoreAcademyMaxCp(player, null);
        player.getCpData().setMaxCP(400);
        PlayerCPManager.updateAcademyMaxCp(player, 100);
        PlayerCPManager.restoreAcademyMaxCp(player, null);
        assertEquals(620, player.getCpData().getMaxCP());
        assertEquals(520, player.getAppliedCommonSkillMaxCpBonus());
        PlayerCPManager.updateAcademyMaxCp(player, 520);
        assertEquals(620, player.getAcademyMaxCp());
    }

    @Test
    void ignoresLaterInflationOfTheMutableMaximum() {
        var player = grownPlayer(600, 500);
        PlayerCPManager.updateAcademyMaxCp(player, 500);
        player.getCpData().setMaxCP(900);
        player.getCpData().setAvailableCP(870);
        PlayerCPManager.restoreAcademyMaxCp(player, null);
        assertEquals(600, player.getCpData().getMaxCP());
        assertEquals(570, player.getCpData().getAvailableCP());
    }

    @Test
    void migratesUninitializedSavesWithoutDoubleApplyingDerivedGrowth() {
        var player = new Player();
        PlayerCPManager.updateAcademyMaxCp(player, 300);
        PlayerCPManager.restoreAcademyMaxCp(player, null);
        assertEquals(400, player.getCpData().getMaxCP());
        assertEquals(400, player.getCpData().getAvailableCP());
        assertTrue(player.isMaxCpInitialized());
        assertFalse(PlayerCPManager.updateAcademyMaxCp(player, 300));
    }

    @Test
    void retainsDebugMaximumWithoutPersistingItAsNaturalGrowth() {
        var player = grownPlayer(600, 500);
        PlayerCPManager.updateAcademyMaxCp(player, 500);
        PlayerCPManager.restoreAcademyMaxCp(player, 250.0f);
        assertEquals(250, player.getCpData().getAvailableCP());
        assertEquals(600, player.getAcademyMaxCp());
        assertEquals(600, player.getCpData().getMaxCP());
        assertFalse(PlayerCPManager.restoreAcademyMaxCp(player, 250.0f));
    }

    @Test
    void refundsForeignAndMissingSkillsButKeepsCategoryAndCommonDebt() {
        var data = new AbilityData();
        data.setAvailableCP(20);
        var timed = new AbilityData.CpOccupationData(15, 20, "academy:arc_generate", false);
        var common = new AbilityData.CpOccupationData(10, 0, "extension:common_skill", true);
        var occupations = new ArrayList<>(List.of(timed, common,
                new AbilityData.CpOccupationData(30, 999, "extension:old_category", false),
                new AbilityData.CpOccupationData(25, 0, "removed:skill", true)));
        var available = Set.of("academy:arc_generate", "extension:common_skill");
        assertTrue(PlayerCPManager.releaseInvalidOccupations(data, occupations, available::contains, 100));
        assertEquals(75, data.getAvailableCP());
        assertEquals(List.of(timed, common), occupations);
        assertEquals(20, timed.getIterationTicks());
        assertFalse(PlayerCPManager.releaseInvalidOccupations(data, occupations, available::contains, 100));
        assertEquals(75, data.getAvailableCP());
    }

    @Test
    void removesMalformedEntriesWithoutCreatingNonFiniteCpOrRefundingTwice() {
        var data = new AbilityData();
        data.setAvailableCP(10);
        var occupations = new ArrayList<AbilityData.CpOccupationData>();
        occupations.add(null);
        occupations.add(new AbilityData.CpOccupationData(Float.NaN, 0, "unknown", true));
        occupations.add(new AbilityData.CpOccupationData(-20, 0, "unknown", true));
        occupations.add(new AbilityData.CpOccupationData(Float.MAX_VALUE, 0, "unknown", true));
        occupations.add(new AbilityData.CpOccupationData(Float.MAX_VALUE, 0, "unknown", true));
        assertTrue(PlayerCPManager.releaseInvalidOccupations(data, occupations, _ -> false, 100));
        assertTrue(occupations.isEmpty());
        assertEquals(100, data.getAvailableCP());
        assertFalse(PlayerCPManager.releaseInvalidOccupations(data, occupations, _ -> false, 100));
    }
}
