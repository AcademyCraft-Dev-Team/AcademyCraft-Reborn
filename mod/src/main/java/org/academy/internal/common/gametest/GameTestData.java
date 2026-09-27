package org.academy.internal.common.gametest;

import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * 26.3 added {@code TestData.dimension}; this factory preserves the previous 11-argument call shape
 * used across the mod's game tests (defaulting to the Overworld).
 */
public final class GameTestData {
    private GameTestData() {
    }

    public static <E> TestData<E> of(
            E environment,
            Identifier structure,
            int maxTicks,
            int setupTicks,
            boolean required,
            Rotation rotation,
            boolean manualOnly,
            int maxAttempts,
            int requiredSuccesses,
            boolean skyAccess,
            int padding
    ) {
        return new TestData<>(
                environment,
                Level.OVERWORLD,
                structure,
                maxTicks,
                setupTicks,
                required,
                rotation,
                manualOnly,
                maxAttempts,
                requiredSuccesses,
                skyAccess,
                padding
        );
    }
}
