package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityFactorProfile;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.aeromanip.Aeromanip;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.ability.level0.Level0;
import org.academy.internal.common.ability.meltdowner.Meltdowner;
import org.academy.internal.common.ability.mentalout.Mentalout;
import org.academy.internal.common.ability.teleport.Teleport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitialAbilitySelectorTest {
    @Test
    void vectorProfileSelectsAccelerator() {
        var categories = activeCategories();

        var selected = InitialAbilitySelector.choose(
                categories,
                AbilityDevelopmentProfiles.ACCELERATOR::weight
        );

        assertSame(categories.getFirst(), selected);
    }

    @Test
    void zeroValuesTreatAllProfiledCategoriesAsTied() {
        var categories = activeCategories();
        var best = InitialAbilitySelector.bestCandidates(categories, _ -> 0.0);

        assertEquals(categories, best);
        assertTrue(categories.contains(InitialAbilitySelector.choose(categories, _ -> 0.0)));
    }

    @Test
    void unprofiledAndLevel0CategoriesAreExcluded() {
        var profiled = new TestCategory(0.1f, AbilityDevelopmentProfiles.RESERVED_A);
        var categories = List.of(new Level0(), new TestCategory(0.1f, null), profiled);

        assertEquals(List.of(profiled), InitialAbilitySelector.bestCandidates(categories, _ -> 0.0));
    }

    private static List<AbilityCategory> activeCategories() {
        return List.of(
                new Accelerator(),
                new Electromaster(),
                new Teleport(),
                new Meltdowner(),
                new Aeromanip(),
                new Darkmatter(),
                new Mentalout()
        );
    }

    private static final class TestCategory extends AbilityCategory {
        private TestCategory(float probability, AbilityFactorProfile profile) {
            super(probability, profile);
        }

        @Override
        public Identifier getDeveloperIcon() {
            return Identifier.parse("academy:test");
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }
    }
}
