package org.academy.internal.server.commands;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.server.time.TemporalChannel;
import org.academy.internal.server.time.TemporalTickDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalTickDebugCommandsTest {
    @Test
    void tickCommandExposesInspectionAndEveryControlScope() {
        var tick = TemporalTickDebugCommands.register().build();

        assertNotNull(tick.getCommand());
        assertNotNull(tick.getChild("targets"));
        assertNotNull(tick.getChild("inspect"));
        assertNotNull(tick.getChild("list"));
        assertNotNull(tick.getChild("clear"));
        assertNotNull(tick.getChild("help"));
        var add = tick.getChild("field").getChild("add");
        assertNotNull(add.getChild("save"));
        assertNotNull(add.getChild("dimension"));
        assertNotNull(add.getChild("sphere"));
        assertNotNull(add.getChild("entities"));
        assertNotNull(tick.getChild("immunity").getChild("add"));
    }

    @Test
    void aliasesAndCommaSeparatedControlSetsAreParsed() throws Exception {
        assertEquals(TemporalChannel.worldSimulation(),
                TemporalTickDebugCommands.parseChannels("world"));
        assertEquals(Set.of(
                        TemporalChannel.ENTITY,
                        TemporalChannel.BLOCK_ENTITY,
                        TemporalChannel.SCHEDULED_BLOCK,
                        TemporalChannel.SCHEDULED_FLUID,
                        TemporalChannel.RANDOM_TICK
                ),
                TemporalTickDebugCommands.parseChannels("spatial"));
        assertEquals(Set.of(
                        TemporalChannel.ENTITY,
                        TemporalChannel.RANDOM_TICK
                ),
                TemporalTickDebugCommands.parseChannels(
                        "entity,random_tick"
                ));
        assertEquals(Set.of(
                        org.academy.api.server.time.TemporalPauseSource.ACADEMY_PAUSE,
                        org.academy.api.server.time.TemporalPauseSource.EXTERNAL_COMPATIBILITY
                ),
                TemporalTickDebugCommands.parseSources(
                        "academy_pause,external_compatibility"
                ));
    }

    @Test
    void reportKeepsEveryTemporalChannelVisible() {
        var channels = Arrays.stream(TemporalChannel.values())
                .map(channel -> new TemporalTickDiagnostics.ChannelState(
                        channel,
                        channel == TemporalChannel.ENTITY,
                        1.0D,
                        channel == TemporalChannel.RANDOM_TICK ? 0.0D : 1.0D,
                        null
                ))
                .toList();
        var snapshot = new TemporalTickDiagnostics(
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO,
                20L,
                20L,
                1L,
                false,
                new TemporalTickDiagnostics.VanillaTickState(
                        20.0F,
                        false,
                        true,
                        false,
                        0,
                        false
                ),
                channels,
                List.of(),
                List.of(),
                new TemporalTickDiagnostics.QueueState(
                        TemporalChannel.SCHEDULED_BLOCK,
                        true,
                        2,
                        0,
                        false,
                        0
                ),
                new TemporalTickDiagnostics.QueueState(
                        TemporalChannel.SCHEDULED_FLUID,
                        true,
                        3,
                        1,
                        false,
                        1
                ),
                new TemporalTickDiagnostics.AccumulatorState(1, 2, 1),
                null
        );

        var report = TemporalTickDebugCommands.format(snapshot);

        for (var channel : TemporalChannel.values()) {
            assertTrue(report.contains(channel.name()));
        }
        assertTrue(report.contains("RANDOM_TICK 1.000x/§cPAUSED"));
        assertTrue(report.contains("Queue SCHEDULED_FLUID: pending=3 frozen=1"));
    }
}
