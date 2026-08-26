package org.academy.internal.common.ability.teleport.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlashingTest {
    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-6);
        assertEquals(expected.y, actual.y, 1.0e-6);
        assertEquals(expected.z, actual.z, 1.0e-6);
    }

    @Test
    void serverDerivesOppositeAndHorizontalDirectionsFromLook() {
        var look = new Vec3(0, 0, 1);
        assertVec(look, Flashing.Server.directionFromLook(look, 0, Flashing.Direction.FORWARD));
        assertVec(look.scale(-1), Flashing.Server.directionFromLook(look, 0, Flashing.Direction.BACK));
        assertVec(new Vec3(1, 0, 0),
                Flashing.Server.directionFromLook(look, 0, Flashing.Direction.LEFT));
        assertVec(new Vec3(-1, 0, 0),
                Flashing.Server.directionFromLook(look, 0, Flashing.Direction.RIGHT));
    }

    @Test
    void dashInvulnerabilityStartsOnInputAndCoversFourTicksAfterCompletion() {
        var playerId = UUID.randomUUID();
        try {
            Flashing.Server.beginDashInvulnerability(playerId);
            assertTrue(Flashing.Server.isDashInvulnerable(playerId, 50));

            Flashing.Server.completeDashInvulnerability(playerId, 100);
            assertTrue(Flashing.Server.isDashInvulnerable(playerId, 100));
            assertTrue(Flashing.Server.isDashInvulnerable(playerId, 103));
            assertFalse(Flashing.Server.isDashInvulnerable(playerId, 104));
        } finally {
            Flashing.Server.clearDashInvulnerability(playerId);
        }
    }

    @Test
    void queuedDashKeepsProtectionOpenUntilEveryAcceptedInputCompletes() {
        var playerId = UUID.randomUUID();
        try {
            Flashing.Server.beginDashInvulnerability(playerId);
            Flashing.Server.beginDashInvulnerability(playerId);
            Flashing.Server.completeDashInvulnerability(playerId, 200);
            assertTrue(Flashing.Server.isDashInvulnerable(playerId, 1_000));

            Flashing.Server.completeDashInvulnerability(playerId, 202);
            assertTrue(Flashing.Server.isDashInvulnerable(playerId, 205));
            assertFalse(Flashing.Server.isDashInvulnerable(playerId, 206));
        } finally {
            Flashing.Server.clearDashInvulnerability(playerId);
        }
    }

    @Test
    void dashInvulnerabilityOnlyBlocksNegativeHealthWrites() {
        var playerId = UUID.randomUUID();
        try {
            Flashing.Server.beginDashInvulnerability(playerId);
            Flashing.Server.completeDashInvulnerability(playerId, 300);
            assertTrue(Flashing.Server.blocksNegativeHealthWrite(playerId, 300, 20.0f, 19.0f));
            assertFalse(Flashing.Server.blocksNegativeHealthWrite(playerId, 300, 20.0f, 20.0f));
            assertFalse(Flashing.Server.blocksNegativeHealthWrite(playerId, 300, 20.0f, 21.0f));
            assertFalse(Flashing.Server.blocksNegativeHealthWrite(playerId, 304, 20.0f, 19.0f));
        } finally {
            Flashing.Server.clearDashInvulnerability(playerId);
        }
    }

    @Test
    void canceledDashInputDoesNotLeaveProtectionBehind() {
        var playerId = UUID.randomUUID();
        try {
            Flashing.Server.beginDashInvulnerability(playerId);
            Flashing.Server.cancelDashInvulnerability(playerId, 400);
            assertFalse(Flashing.Server.isDashInvulnerable(playerId, 400));
        } finally {
            Flashing.Server.clearDashInvulnerability(playerId);
        }
    }

    @Test
    void autoEscapePrefersTheDirectionAwayFromTheAttacker() {
        assertVec(new Vec3(1, 0, 0), Flashing.Server.autoEscapeDirection(
                Vec3.ZERO,
                new Vec3(-2, 4, 0),
                0.0
        ));
        assertVec(new Vec3(0, 0, 1), Flashing.Server.autoEscapeDirection(
                Vec3.ZERO,
                Vec3.ZERO,
                Math.PI / 2.0
        ));
    }

    @Test
    void autoEscapeCooldownLastsTenSeconds() {
        var playerId = UUID.randomUUID();
        try {
            assertTrue(Flashing.Server.isAutoEscapeReady(playerId, 100));
            Flashing.Server.markAutoEscapeTriggered(playerId, 100);
            assertFalse(Flashing.Server.isAutoEscapeReady(playerId, 299));
            assertTrue(Flashing.Server.isAutoEscapeReady(playerId, 300));
        } finally {
            Flashing.Server.clearAutoEscapeCooldown(playerId);
        }
    }
}
