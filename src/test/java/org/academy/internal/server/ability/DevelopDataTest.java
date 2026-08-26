package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.DevelopAction;
import org.academy.api.common.wireless.WirelessUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevelopDataTest {
    @Test
    void cumulativeEnergyTargetsChargeTheExactNonDivisibleTotal() {
        assertEquals(0, DevelopData.targetEnergy(7, 4, 0));
        assertEquals(1, DevelopData.targetEnergy(7, 4, 1));
        assertEquals(3, DevelopData.targetEnergy(7, 4, 2));
        assertEquals(5, DevelopData.targetEnergy(7, 4, 3));
        assertEquals(7, DevelopData.targetEnergy(7, 4, 4));
        assertEquals(7, DevelopData.targetEnergy(7, 4, 20));
    }

    @Test
    void normalizesDevelopmentTarget() {
        var action = new DevelopAction() {
            @Override
            public int getTotalTicks() {
                return 20;
            }

            @Override
            public void onComplete(ServerPlayer player,
                                   WirelessUser developer) {
            }

            @Override
            public String getTargetId() {
                return "academy:test_skill";
            }
        };

        assertEquals("academy:test_skill", DevelopData.targetIdOf(action));
        assertEquals("", DevelopData.targetIdOf(null));
    }
}
