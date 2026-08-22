package org.academy.internal.common.ability.darkmatter.creature;

import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DarkmatterCreatureBlueprintTest {
    @Test
    void defaultBlueprintIsValidAcrossLevelsAndKeepsIndependentPartPools() {
        for (var level = 1; level <= 5; level++) {
            var blueprint = DarkmatterCreatureBlueprint.defaultFor(0, level);
            assertTrue(blueprint.validate(level).isEmpty());
            assertEquals(level * 25, blueprint.headAlpha());
            assertEquals(level, blueprint.averageGammaPower(level), 0.0001f);
        }
    }

    @Test
    void rejectsMaliciousInvestmentMissingPartsAndOverBudgetModules() {
        var invalid = new DarkmatterCreatureBlueprint("bad", 6,
                "missing:head", DarkmatterCreatureRegistries.TORSO_WALK.toString(),
                DarkmatterCreatureRegistries.LIMBS_GUARD.toString(),
                DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString(),
                300, -1, 0, 0,
                List.of(DarkmatterCreatureRegistries.MODULE_SELF_REPAIR.toString(),
                        DarkmatterCreatureRegistries.MODULE_EXCAVATION.toString()));
        var errors = invalid.validate(5);
        assertTrue(errors.contains("investment"));
        assertTrue(errors.contains("head"));
        assertTrue(errors.contains("head_phase"));
        assertTrue(errors.contains("torso_phase"));
        assertTrue(errors.stream().anyMatch(value -> value.startsWith("module_budget:")));
    }

    @Test
    void moduleBudgetAndM1VirtualInvestmentDoNotChangeReservation() {
        var blueprint = new DarkmatterCreatureBlueprint("guard", 10,
                DarkmatterCreatureRegistries.HEAD_JAW.toString(),
                DarkmatterCreatureRegistries.TORSO_WALK.toString(),
                DarkmatterCreatureRegistries.LIMBS_GUARD.toString(),
                DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString(),
                50, 50, 50, 50,
                List.of(DarkmatterCreatureRegistries.MODULE_GUARD.toString()));
        assertEquals(2, blueprint.moduleBudget());
        assertEquals(1, blueprint.moduleCost());
        assertEquals(10, blueprint.investment());
        assertEquals(11, blueprint.effectiveInvestment(1), 0.0001);
        assertTrue(blueprint.createBaseStats(1, 1).maxHealth
                > blueprint.createBaseStats(0, 1).maxHealth);
    }
}
