package org.academy.internal.server.entity;

import org.academy.api.server.entity.SurvivalDefenseAspect;
import org.academy.api.server.entity.SurvivalDefenseProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurvivalDefenseRuntimeTest {
    @Test
    void contributionsMergeIndependentlyByAspectAndHealthFloor() {
        var deathGuard = new SurvivalDefenseProfile(
                40,
                0.0f,
                EnumSet.of(SurvivalDefenseAspect.DEATH_STATE)
        );
        var lowHealthGuard = new SurvivalDefenseProfile(
                10,
                1.0f,
                EnumSet.of(SurvivalDefenseAspect.HEALTH_FLOOR, SurvivalDefenseAspect.REMOVAL)
        );
        var strongHealthGuard = new SurvivalDefenseProfile(
                100,
                4.0f,
                EnumSet.of(SurvivalDefenseAspect.HEALTH_FLOOR)
        );

        var effective = SurvivalDefenseRuntime.combine(List.of(
                deathGuard,
                lowHealthGuard,
                strongHealthGuard
        ));

        assertEquals(40, effective.strength(SurvivalDefenseAspect.DEATH_STATE));
        assertEquals(100, effective.strength(SurvivalDefenseAspect.HEALTH_FLOOR));
        assertEquals(10, effective.strength(SurvivalDefenseAspect.REMOVAL));
        assertEquals(0, effective.strength(SurvivalDefenseAspect.LEVEL_MEMBERSHIP));
        assertEquals(4.0f, effective.minimumHealth());
    }
}
