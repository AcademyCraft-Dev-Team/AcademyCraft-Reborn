package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.AbilityCategory;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot, server-authoritative P.R.O.P.S recommendations. */
public final class InitialAbilityRecommendationCache {
    public static final long EXPIRATION_TICKS = 1_200L;

    private final Map<UUID, Recommendation> recommendations = new ConcurrentHashMap<>();

    public void put(
            UUID playerId,
            AbilityCategory category,
            Identifier dimension,
            BlockPos developerPos,
            long gameTime
    ) {
        put(playerId, category, dimension, "block:" + developerPos.asLong(), gameTime);
    }

    public void put(
            UUID playerId,
            AbilityCategory category,
            Identifier dimension,
            String developerKey,
            long gameTime
    ) {
        recommendations.put(playerId, new Recommendation(
                category,
                dimension,
                developerKey,
                gameTime + EXPIRATION_TICKS
        ));
    }

    public @Nullable AbilityCategory consume(
            UUID playerId,
            Identifier dimension,
            BlockPos developerPos,
            long gameTime
    ) {
        return consume(playerId, dimension, "block:" + developerPos.asLong(), gameTime);
    }

    public @Nullable AbilityCategory consume(
            UUID playerId,
            Identifier dimension,
            String developerKey,
            long gameTime
    ) {
        var recommendation = recommendations.remove(playerId);
        if (recommendation == null
                || gameTime >= recommendation.expiresAt
                || !recommendation.dimension.equals(dimension)
                || !recommendation.developerKey.equals(developerKey)) {
            return null;
        }
        return recommendation.category;
    }

    public void clear(UUID playerId) {
        recommendations.remove(playerId);
    }

    public int size() {
        return recommendations.size();
    }

    private record Recommendation(
            AbilityCategory category,
            Identifier dimension,
            String developerKey,
            long expiresAt
    ) {
    }
}
