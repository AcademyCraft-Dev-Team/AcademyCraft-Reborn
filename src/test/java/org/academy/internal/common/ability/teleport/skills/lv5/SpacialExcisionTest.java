package org.academy.internal.common.ability.teleport.skills.lv5;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacialExcisionTest {
    @Test
    void activationUsesThePublishedCostAndDuration() {
        assertEquals(100, SpacialExcision.ACTIVATION_CP);
        assertEquals(600, SpacialExcision.DURATION_TICKS);
    }

    @Test
    void successfulSegmentsAreStoredAndContextCleanupClearsCombatState() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));
        var recording = section(source, "private void recordSuccessfulTeleport(",
                "\n        private void broadcast(");
        var cleanup = section(source, "protected void onUnregistered()",
                "\n        private record RecordedSegment(");

        assertTrue(recording.contains("segments.add(segment);"));
        assertFalse(recording.contains("combatScheduler"));
        assertFalse(recording.contains("Server.registerCombat"));
        assertTrue(cleanup.contains("segments.clear();"));
        assertFalse(cleanup.contains("Server.unregisterCombat"));
    }

    @Test
    void serverTickUsesContextQueriesWithoutGlobalCoordinators() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));
        var tick = section(source, "private static void tick(MinecraftServer server)",
                "@EventBusSubscriber");

        assertTrue(tick.contains("context.tickCombat(now);"));
        assertFalse(tick.contains("COMBAT_COORDINATORS"));
        assertFalse(tick.contains("PULL_COORDINATORS"));
        assertFalse(tick.contains("ServerCombatCoordinator"));
        assertFalse(tick.contains("PullCoordinator"));
    }

    @Test
    void serverTickOnlyProcessesContextsOwnedByTheTickingServer() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));
        var tick = section(source, "private static void tick(MinecraftServer server)",
                "@EventBusSubscriber");

        assertTrue(tick.contains("if (context.server == server && !context.ended)"));
    }

    @Test
    void combatQueryAppliesPullImmediatelyWithoutPostTickCoordinator() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));
        var query = section(source, "private void tickCombat(",
                "\n        private boolean isEligibleCombatTarget(");

        assertTrue(query.contains("target.setDeltaMovement("));
        assertTrue(query.contains("attractionDistance("));
        assertFalse(query.contains("queuePull("));
        assertFalse(source.contains("EntityTickEvent.Post"));
    }

    @Test
    void serverOnlyFieldMathIsInternalToTheSkill() throws IOException {
        var skill = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));

        assertTrue(skill.contains("static final class Field"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcisionField.java")));
    }

    @Test
    void guiResizeDoesNotReachSpatialExcision() throws IOException {
        var client = Files.readString(Path.of(
                "src/main/java/org/academy/AcademyCraftClient.java"));
        var resize = section(client, "public static void resize(int width, int height)",
                "@SubscribeEvent");

        assertTrue(resize.contains("HudManager.INSTANCE.resize(width, height);"));
        assertFalse(resize.contains("SpacialExcisionVfxClient.resize"));
    }

    private static String section(String source, String startMarker, String endMarker) {
        var start = source.indexOf(startMarker);
        var end = source.indexOf(endMarker, start);
        return start >= 0 && end > start ? source.substring(start, end) : "";
    }
}
