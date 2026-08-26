package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.ProficiencyPolicy;

/**
 * Shared finite-damage CP calculation for vector reflection and vector reduction.
 */
public final class VectorDefenseProficiency {
    private static final float[] DAMAGE_COST_MULTIPLIERS = {2.0f, 1.0f, 0.5f, 0.5f};

    private VectorDefenseProficiency() {
    }

    public static float costMultiplier(int milestone) {
        if (milestone < 0) return 1.0f;
        return DAMAGE_COST_MULTIPLIERS[Math.clamp(milestone, 0, 3)];
    }

    public static int effectiveMilestone(ServerPlayer player, Skill skill) {
        return ProficiencyPolicy.server(player).enabled()
                ? skill.getProficiencyMilestone(player)
                : -1;
    }

    public static Result calculate(
            float incomingDamage,
            float availableCp,
            float calculationIntensity,
            int milestone,
            float freeDamageThreshold,
            boolean debugMode
    ) {
        if (!(incomingDamage > 0.0f) || !Float.isFinite(incomingDamage)) {
            return Result.NONE;
        }
        if (debugMode) return new Result(incomingDamage, 0.0f, 0.0f);
        if (milestone >= 3 && Float.isFinite(freeDamageThreshold)
                && incomingDamage < Math.max(0.0f, freeDamageThreshold)) {
            return new Result(incomingDamage, 0.0f, 0.0f);
        }
        if (!(availableCp > 0.0f) || !Float.isFinite(availableCp)
                || !(calculationIntensity > 0.0f) || !Float.isFinite(calculationIntensity)) {
            return new Result(0.0f, incomingDamage, 0.0f);
        }

        var multiplier = costMultiplier(milestone);
        var actualCostPerDamage = multiplier * calculationIntensity;
        if (!(actualCostPerDamage > 0.0f) || !Float.isFinite(actualCostPerDamage)) {
            return new Result(0.0f, incomingDamage, 0.0f);
        }
        var processedDamage = Math.min(incomingDamage, availableCp / actualCostPerDamage);
        if (!(processedDamage > 0.0f) || !Float.isFinite(processedDamage)) {
            return new Result(0.0f, incomingDamage, 0.0f);
        }
        var baseCpCost = processedDamage * multiplier;
        if (!Float.isFinite(baseCpCost)) {
            return new Result(0.0f, incomingDamage, 0.0f);
        }
        var remainingDamage = Math.max(0.0f, incomingDamage - processedDamage);
        return new Result(processedDamage, remainingDamage, baseCpCost);
    }

    public record Result(float processedDamage, float remainingDamage, float baseCpCost) {
        private static final Result NONE = new Result(0.0f, 0.0f, 0.0f);

        public boolean isFull() {
            return !(remainingDamage > 1.0E-5f);
        }
    }
}
