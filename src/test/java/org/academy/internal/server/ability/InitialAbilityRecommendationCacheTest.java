package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InitialAbilityRecommendationCacheTest {
    private static final BlockPos DEVELOPER_POS = new BlockPos(10, 64, 10);
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    @Test
    void consumesMatchingRecommendationOnce() {
        var cache = new InitialAbilityRecommendationCache();
        var playerId = UUID.randomUUID();
        var category = new TestCategory();
        cache.put(playerId, category, OVERWORLD, DEVELOPER_POS, 100L);

        assertSame(category, cache.consume(
                playerId, OVERWORLD, DEVELOPER_POS, 100L +
                        InitialAbilityRecommendationCache.EXPIRATION_TICKS - 1
        ));
        assertNull(cache.consume(playerId, OVERWORLD, DEVELOPER_POS, 101L));
        assertEquals(0, cache.size());
    }

    @Test
    void expiresAtSixtySeconds() {
        var cache = new InitialAbilityRecommendationCache();
        var playerId = UUID.randomUUID();
        cache.put(playerId, new TestCategory(), OVERWORLD, DEVELOPER_POS, 0L);

        assertNull(cache.consume(
                playerId,
                OVERWORLD,
                DEVELOPER_POS,
                InitialAbilityRecommendationCache.EXPIRATION_TICKS
        ));
    }

    @Test
    void rejectsAndConsumesMismatchedDeveloperContext() {
        var cache = new InitialAbilityRecommendationCache();
        var playerId = UUID.randomUUID();
        cache.put(playerId, new TestCategory(), OVERWORLD, DEVELOPER_POS, 0L);

        assertNull(cache.consume(playerId, NETHER, DEVELOPER_POS, 1L));
        assertNull(cache.consume(playerId, OVERWORLD, DEVELOPER_POS, 1L));
    }

    @Test
    void clearRemovesPendingRecommendation() {
        var cache = new InitialAbilityRecommendationCache();
        var playerId = UUID.randomUUID();
        cache.put(playerId, new TestCategory(), OVERWORLD, DEVELOPER_POS, 0L);

        cache.clear(playerId);

        assertEquals(0, cache.size());
    }

    @Test
    void portableDeveloperContextIsMatchedIndependentlyFromBlockPositions() {
        var cache = new InitialAbilityRecommendationCache();
        var playerId = UUID.randomUUID();
        var category = new TestCategory();
        cache.put(playerId, category, OVERWORLD, "tablet:MAIN_HAND", 20L);

        assertNull(cache.consume(playerId, OVERWORLD, "tablet:OFF_HAND", 21L));
        cache.put(playerId, category, OVERWORLD, "tablet:MAIN_HAND", 22L);
        assertSame(category, cache.consume(playerId, OVERWORLD, "tablet:MAIN_HAND", 23L));
    }

    private static final class TestCategory extends AbilityCategory {
        private TestCategory() {
            super(0.1f, AbilityDevelopmentProfiles.ACCELERATOR);
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
