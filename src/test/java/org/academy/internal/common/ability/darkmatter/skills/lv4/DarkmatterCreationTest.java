package org.academy.internal.common.ability.darkmatter.skills.lv4;

import io.netty.buffer.Unpooled;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterCreationTest {
    @Test
    void contractLimitsAndReservationMatchAudit() {
        assertEquals(16, DarkmatterCreation.MAX_CREATURES);
        assertEquals(16, DarkmatterCreation.MAX_BEETLES);
        assertEquals(5.0f, DarkmatterCreation.MIN_INVESTMENT, 0.0001f);
        assertEquals(40.0f, DarkmatterCreation.RESERVED_CP_PER_BEETLE, 0.0001f);
    }

    @Test
    void secondMilestoneImprovesPursuitWithoutRaisingCountCap() {
        assertEquals(1.0, DarkmatterCreation.followSpeed(1), 0.0001);
        assertEquals(1.2, DarkmatterCreation.followSpeed(2), 0.0001);
        assertEquals(1.0, DarkmatterCreation.targetingRange(1), 0.0001);
        assertEquals(1.2, DarkmatterCreation.targetingRange(2), 0.0001);
        assertEquals(100, DarkmatterCreation.stuckTeleportTicks(1));
        assertEquals(60, DarkmatterCreation.stuckTeleportTicks(2));
    }

    @Test
    void thirdMilestoneBoostsModulesAndGammaRepeatOnly() {
        assertEquals(1.25f, DarkmatterCreation.moduleValueMultiplier(3), 0.0001f);
        assertEquals(100, DarkmatterCreation.gammaRepeatTicks(2));
        assertEquals(80, DarkmatterCreation.gammaRepeatTicks(3));
        assertEquals(1.0f, DarkmatterCreation.swarmDamageMultiplier(3, 8), 0.0001f);
        Assertions.assertFalse(DarkmatterCreation.unlocksSwarmCommand(3));
    }

    @Test
    void summonRequestCarriesTheEditedBlueprintAtomically() {
        var expected = DarkmatterCreatureBlueprint.defaultFor(2, 5);
        var buffer = Unpooled.buffer();
        DarkmatterCreation.SummonPacket.CODEC.encode(buffer,
                new DarkmatterCreation.SummonPacket(2, expected));
        var decoded = DarkmatterCreation.SummonPacket.CODEC.decode(buffer);
        assertEquals(2, decoded.slot);
        assertNotNull(decoded.blueprint);
        assertEquals(expected.name(), decoded.blueprint.name());
        assertEquals(expected.investment(), decoded.blueprint.investment());

        var quickBuffer = Unpooled.buffer();
        DarkmatterCreation.SummonPacket.CODEC.encode(quickBuffer,
                new DarkmatterCreation.SummonPacket(1));
        assertNull(DarkmatterCreation.SummonPacket.CODEC.decode(quickBuffer).blueprint);
    }

    @Test
    void everySummonFailureReasonRoundTripsToTheClient() {
        for (var result : DarkmatterCreation.SummonResult.values()) {
            var buffer = Unpooled.buffer();
            DarkmatterCreation.SummonResultPacket.CODEC.encode(buffer,
                    new DarkmatterCreation.SummonResultPacket(result));
            assertEquals(result,
                    DarkmatterCreation.SummonResultPacket.CODEC.decode(buffer).result);
        }
    }
}
