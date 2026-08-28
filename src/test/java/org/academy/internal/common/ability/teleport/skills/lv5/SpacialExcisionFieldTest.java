package org.academy.internal.common.ability.teleport.skills.lv5;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacialExcisionFieldTest {
    private static final double EPSILON = 1.0e-9;
    private static final Vec3 START = Vec3.ZERO;
    private static final Vec3 END = new Vec3(10.0, 0.0, 0.0);

    @Test
    void combatPolicyKeepsTheOriginalCrackRangeAndTiming() {
        assertEquals(1.5, SpacialExcision.Field.DAMAGE_HALF_EXTENT, EPSILON);
        assertEquals(3.5, SpacialExcision.Field.ATTRACTION_HALF_EXTENT, EPSILON);
        assertEquals(10, SpacialExcision.Field.PULSE_INTERVAL_TICKS);
        assertEquals(20, SpacialExcision.Field.SPAWN_STRIKE_WINDOW_TICKS);
        assertEquals(12.0, SpacialExcision.Field.PERSISTENT_DAMAGE, EPSILON);
        assertEquals(40.0, SpacialExcision.Field.SPAWN_STRIKE_DAMAGE, EPSILON);
    }

    @Test
    void originalThreeByThreeCrackDamageRangeIsSmallerThanTheAttractionRange() {
        assertTrue(SpacialExcision.Field.intersectsExpandedAabb(
                START, END, bounds(5.0, 1.4, 0.0),
                SpacialExcision.Field.DAMAGE_HALF_EXTENT));
        assertFalse(SpacialExcision.Field.intersectsExpandedAabb(
                START, END, bounds(5.0, 1.6, 0.0),
                SpacialExcision.Field.DAMAGE_HALF_EXTENT));
        assertTrue(SpacialExcision.Field.intersectsExpandedAabb(
                START, END, bounds(5.0, 3.4, 0.0),
                SpacialExcision.Field.ATTRACTION_HALF_EXTENT));
        assertFalse(SpacialExcision.Field.intersectsExpandedAabb(
                START, END, bounds(5.0, 3.6, 0.0),
                SpacialExcision.Field.ATTRACTION_HALF_EXTENT));
    }

    @Test
    void tickActionsRespectSpawnWindowAndPersistentCooldown() {
        var inside = bounds(5.0, 1.4, 0.0);
        assertTrue(SpacialExcision.Field.tickActions(
                0, true, true, inside, START, END).spawnStrike());
        assertTrue(SpacialExcision.Field.tickActions(
                19, true, false, inside, START, END).spawnStrike());
        assertFalse(SpacialExcision.Field.tickActions(
                20, true, true, inside, START, END).spawnStrike());
        assertTrue(SpacialExcision.Field.tickActions(
                10, false, true, inside, START, END).persistentDamage());
        assertFalse(SpacialExcision.Field.tickActions(
                10, false, false, inside, START, END).persistentDamage());
    }

    @Test
    void attractionRemainsFiniteBoundedAndStrongerNearTheCrack() {
        var near = SpacialExcision.Field.pulledVelocity(
                Vec3.ZERO, bounds(5.0, 0.0, 0.5), START, END);
        var far = SpacialExcision.Field.pulledVelocity(
                Vec3.ZERO, bounds(5.0, 0.0, 3.0), START, END);

        assertTrue(SpacialExcision.Field.isFinite(near));
        assertTrue(SpacialExcision.Field.isFinite(far));
        assertTrue(Math.abs(near.z) > Math.abs(far.z));
        assertTrue(near.length() <= SpacialExcision.Field.MAX_PULL_SPEED + EPSILON);
        assertEquals(Vec3.ZERO, SpacialExcision.Field.pulledVelocity(
                Vec3.ZERO, bounds(5.0, 0.0, 3.6), START, END));
    }

    @Test
    void corridorIntersectionDoesNotAllocatePerCandidateScratchArrays() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/teleport/skills/lv5/SpacialExcision.java"));
        assertFalse(source.contains("new double[]{"));
        assertFalse(source.contains("allFinite(double..."));
    }

    private static AABB bounds(double x, double y, double z) {
        return new AABB(x - 0.05, y - 0.05, z - 0.05,
                x + 0.05, y + 0.05, z + 0.05);
    }
}
