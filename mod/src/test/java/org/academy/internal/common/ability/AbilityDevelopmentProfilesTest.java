package org.academy.internal.common.ability;

import org.academy.api.common.ability.AbilityFactorProfile;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.aeromanip.Aeromanip;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.ability.meltdowner.Meltdowner;
import org.academy.internal.common.ability.mentalout.Mentalout;
import org.academy.internal.common.ability.teleport.Teleport;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityDevelopmentProfilesTest {
    @Test
    void coreAndReservedProfilesRemainDistinctAndBalanced() {
        var profiles = AbilityDevelopmentProfiles.allProfiles();

        assertEquals(10, profiles.size());
        assertEquals(10, new HashSet<>(profiles).size());
        for (var source : profiles) {
            assertEquals(44.0, source.magnitudeSquared(), 1.0E-9);
            assertEquals(44.0, source.score(source::weight), 1.0E-9);
            for (var target : profiles) {
                if (source == target) continue;
                assertTrue(target.score(source::weight) <= 39.0 + 1.0E-9,
                        () -> source + " is too close to " + target);
            }
        }
    }

    @Test
    void activeCategoriesUseOnlyActiveProfiles() {
        var attached = List.of(
                new Accelerator().getDevelopmentProfile().orElseThrow(),
                new Electromaster().getDevelopmentProfile().orElseThrow(),
                new Teleport().getDevelopmentProfile().orElseThrow(),
                new Meltdowner().getDevelopmentProfile().orElseThrow(),
                new Aeromanip().getDevelopmentProfile().orElseThrow(),
                new Darkmatter().getDevelopmentProfile().orElseThrow(),
                new Mentalout().getDevelopmentProfile().orElseThrow()
        );

        assertEquals(AbilityDevelopmentProfiles.activeProfiles(), attached);
        assertTrue(AbilityDevelopmentProfiles.reservedProfiles().stream().noneMatch(attached::contains));
    }

    @Test
    void darkMatterUsesLessMuscleThanElectricalAbilities() {
        var darkMatter = AbilityDevelopmentProfiles.DARKMATTER.muscleStrength();

        assertTrue(darkMatter < AbilityDevelopmentProfiles.ELECTROMASTER.muscleStrength());
        assertTrue(darkMatter < AbilityDevelopmentProfiles.MELTDOWNER.muscleStrength());
    }

    @Test
    void vectorInputScoresAcceleratorHighest() {
        AbilityFactorProfile winner = null;
        var winnerScore = Double.NEGATIVE_INFINITY;
        for (var candidate : AbilityDevelopmentProfiles.activeProfiles()) {
            var score = candidate.score(AbilityDevelopmentProfiles.ACCELERATOR::weight);
            if (score > winnerScore) {
                winner = candidate;
                winnerScore = score;
            }
        }

        assertSame(AbilityDevelopmentProfiles.ACCELERATOR, winner);
        assertEquals(44.0, winnerScore, 1.0E-9);
    }
}
