package org.academy.api.common.ability.pakcet;

import io.netty.buffer.Unpooled;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.AbilityData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncAbilityDataPacketTest {
    @Test
    void summarizesTimedOccupationsBySkill() {
        var summaries = SyncAbilityDataPacket.summarizeOccupations(List.of(
                new AbilityData.CpOccupationData(10.0f, 4, "academy:self_teleport", false),
                new AbilityData.CpOccupationData(5.0f, 9, "academy:self_teleport", false),
                new AbilityData.CpOccupationData(20.0f, 0, "academy:self_teleport", true)
        ));

        assertEquals(1, summaries.size());
        var summary = summaries.getFirst();
        assertEquals("academy:self_teleport", summary.skillId());
        assertEquals(2, summary.stackCount());
        assertEquals(9, summary.remainingIterationPoints());
        assertEquals(15.0f, summary.occupiedCp(), 0.0001f);
    }

    @Test
    void codecRoundTripPreservesClientAvailabilityState() {
        var abilityData = AbilityData.builder()
                .maxCP(300.0f)
                .availableCP(125.0f)
                .level(AbilityLevel.LEVEL3)
                .status(AbilityData.Status.NORMAL)
                .currSP(900)
                .maxSP(2_000)
                .build();
        var packet = new SyncAbilityDataPacket(
                abilityData,
                0.75f,
                List.of(new AbilityData.CpOccupationData(
                        30.0f,
                        10,
                        "academy:flesh_ripping",
                        false
                ))
        );
        var buffer = Unpooled.buffer();

        SyncAbilityDataPacket.CODEC.encode(buffer, packet);
        var decoded = SyncAbilityDataPacket.CODEC.decode(buffer);

        assertEquals(125.0f, decoded.getAbilityData().getAvailableCP(), 0.0001f);
        assertEquals(AbilityData.FIXED_MAX_SP, decoded.getAbilityData().getMaxSP());
        assertEquals(0.75f, decoded.getCalculationIntensity(), 0.0001f);
        assertEquals(1, decoded.getSkillOccupations().size());
        assertEquals("academy:flesh_ripping", decoded.getSkillOccupations().getFirst().skillId());
    }
}
