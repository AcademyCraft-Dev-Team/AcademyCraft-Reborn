package org.academy.api.common.ability;

import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable scalar modifiers unlocked by the 1000/2000/3000 proficiency milestones.
 * Values are indexed by the number of reached milestones (0..3), never by skill level.
 */
public final class SkillProficiencyProfile {
    public static final SkillProficiencyProfile NONE = builder().build();

    private final Map<CostKind, float[]> costMultipliers;
    private final int[] iterationTicks;

    private SkillProficiencyProfile(Builder builder) {
        var multipliers = new EnumMap<CostKind, float[]>(CostKind.class);
        for (var kind : CostKind.values()) {
            multipliers.put(kind, builder.costMultipliers.get(kind).clone());
        }
        costMultipliers = Map.copyOf(multipliers);
        iterationTicks = builder.iterationTicks.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public float adjustCost(CostKind kind, int milestone, float cost) {
        if (!Float.isFinite(cost) || cost < 0.0f) return Float.NaN;
        var multiplier = costMultipliers.get(kind)[clampMilestone(milestone)];
        var adjusted = cost * multiplier;
        return Float.isFinite(adjusted) ? adjusted : Float.NaN;
    }

    public int resolveIterationTicks(int milestone, int baseTicks) {
        var override = iterationTicks[clampMilestone(milestone)];
        return override > 0 ? override : baseTicks;
    }

    public float getCostMultiplier(CostKind kind, int milestone) {
        return costMultipliers.get(kind)[clampMilestone(milestone)];
    }

    private static int clampMilestone(int milestone) {
        return Math.clamp(milestone, 0, 3);
    }

    public enum CostKind {
        CAST,
        CONTINUOUS,
        MAINTENANCE,
        DYNAMIC
    }

    public static final class Builder {
        private final EnumMap<CostKind, float[]> costMultipliers = new EnumMap<>(CostKind.class);
        private int[] iterationTicks = {-1, -1, -1, -1};

        private Builder() {
            for (var kind : CostKind.values()) {
                costMultipliers.put(kind, new float[]{1.0f, 1.0f, 1.0f, 1.0f});
            }
        }

        public Builder costs(CostKind kind, float base, float at1000, float at2000, float at3000) {
            costMultipliers.put(kind, validatedMultipliers(base, at1000, at2000, at3000));
            return this;
        }

        public Builder allCosts(float base, float at1000, float at2000, float at3000) {
            var values = validatedMultipliers(base, at1000, at2000, at3000);
            for (var kind : CostKind.values()) costMultipliers.put(kind, values.clone());
            return this;
        }

        public Builder iterationTicks(int base, int at1000, int at2000, int at3000) {
            iterationTicks = new int[]{
                    validatedIteration(base),
                    validatedIteration(at1000),
                    validatedIteration(at2000),
                    validatedIteration(at3000)
            };
            return this;
        }

        public SkillProficiencyProfile build() {
            return new SkillProficiencyProfile(this);
        }

        private static float[] validatedMultipliers(float... values) {
            for (var value : values) {
                if (!Float.isFinite(value) || value < 0.0f) {
                    throw new IllegalArgumentException("Proficiency cost multipliers must be finite and non-negative");
                }
            }
            return values;
        }

        private static int validatedIteration(int ticks) {
            if (ticks == -1 || ticks > 0) return ticks;
            throw new IllegalArgumentException("Proficiency iteration ticks must be -1 or positive");
        }
    }
}
