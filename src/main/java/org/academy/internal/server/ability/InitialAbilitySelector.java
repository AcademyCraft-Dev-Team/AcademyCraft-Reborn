package org.academy.internal.server.ability;

import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.attribute.AbilityFactor;
import org.academy.api.common.util.MathUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Pure scoring and tie handling for P.R.O.P.S initial ability prediction.
 */
public final class InitialAbilitySelector {
    private static final double SCORE_EPSILON = 1.0E-9;

    private InitialAbilitySelector() {
    }

    public static @Nullable AbilityCategory choose(
            Iterable<AbilityCategory> categories,
            ToDoubleFunction<AbilityFactor> factorValues
    ) {
        var candidates = bestCandidates(categories, factorValues);
        return chooseWeighted(candidates);
    }

    public static List<AbilityCategory> bestCandidates(
            Iterable<AbilityCategory> categories,
            ToDoubleFunction<AbilityFactor> factorValues
    ) {
        var best = new ArrayList<AbilityCategory>();
        var bestScore = Double.NEGATIVE_INFINITY;
        for (var category : categories) {
            var profile = category.getDevelopmentProfile();
            if (profile.isEmpty()) continue;
            var score = profile.get().score(factorValues);
            if (best.isEmpty() || scoreGreaterThan(score, bestScore)) {
                best.clear();
                best.add(category);
                bestScore = score;
            } else if (scoresEqual(score, bestScore)) {
                best.add(category);
            }
        }
        return List.copyOf(best);
    }

    public static @Nullable AbilityCategory chooseWeighted(List<AbilityCategory> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
        for (var category : candidates) {
            weightedRandom.addItem(category, category.getProbability());
        }
        var selected = weightedRandom.getRandomItem();
        return selected != null ? selected : candidates.get(MathUtil.RANDOM.nextInt(candidates.size()));
    }

    private static boolean scoreGreaterThan(double score, double bestScore) {
        return score - bestScore > tolerance(score, bestScore);
    }

    private static boolean scoresEqual(double first, double second) {
        return Math.abs(first - second) <= tolerance(first, second);
    }

    private static double tolerance(double first, double second) {
        return SCORE_EPSILON * Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    }
}
