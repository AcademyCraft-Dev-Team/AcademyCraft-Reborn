package org.academy.api.server.time;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalFieldTest {
    private static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "temporal_field")
    );

    @Test
    void fieldOwnsAnImmutableChannelSnapshot() {
        var channels = EnumSet.of(TemporalChannel.ENTITY);
        var field = TemporalField.scale(
                TemporalScope.dimension(DIMENSION),
                channels,
                0.5D
        );
        channels.add(TemporalChannel.BLOCK_ENTITY);

        assertEquals(Set.of(TemporalChannel.ENTITY), field.channels());
        assertThrows(
                UnsupportedOperationException.class,
                () -> field.channels().add(TemporalChannel.BLOCK_ENTITY)
        );
    }

    @Test
    void worldPresetIncludesLogicalServerClockButExcludesPresentationClock() {
        var channels = TemporalChannel.worldSimulation();
        assertTrue(channels.contains(TemporalChannel.ENTITY));
        assertTrue(channels.contains(TemporalChannel.LEVEL_CLOCK));
        assertTrue(channels.contains(TemporalChannel.SERVER_CLOCK));
        assertTrue(channels.contains(TemporalChannel.WORLD_BORDER));
        assertTrue(channels.contains(TemporalChannel.NATURAL_SPAWNING));
        assertTrue(channels.contains(TemporalChannel.DRAGON_FIGHT));
        assertFalse(channels.contains(TemporalChannel.CLIENT_VISUAL));
    }

    @Test
    void fieldRejectsEmptyChannelsAndUnsafeScale() {
        assertThrows(IllegalArgumentException.class, () -> TemporalField.scale(
                TemporalScope.save(),
                Set.of(),
                1.0D
        ));
        assertThrows(IllegalArgumentException.class, () -> TemporalField.scale(
                TemporalScope.save(),
                Set.of(TemporalChannel.ENTITY),
                TemporalScale.DEFAULT_MAX_SCALE + 0.1D
        ));
    }
}
