package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.academy.api.common.structure.StructurePhysicsLease.State.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeStructureControlSlotTest {
    private static final UUID OWNER = new UUID(1, 2);
    private static final UUID COMPETITOR = new UUID(3, 4);
    private static final StructurePhysicsControl FORCE = new StructurePhysicsControl.Force(
            new Vec3(0.5, 0.5, 0.5), new Vec3(0, 10, 0));
    private static final StructurePhysicsControl ANCHOR = new StructurePhysicsControl.Anchor(
            new Vec3(0.5, 0.5, 0.5), new Vec3(2, 3, 4), Vec3.ZERO, 10, 1, 100);

    @Test
    void consumedLeasedForceStillExcludesEveryOtherWriterUntilExpiry() {
        var slot = new NativeStructureControlSlot();
        assertFalse(slot.occupied());
        var ticket = slot.acquire(OWNER, FORCE, 2).orElseThrow();
        slot.consumeForce();
        assertTrue(slot.occupied());
        assertTrue(slot.control().isEmpty());
        assertTrue(ticket.control().isEmpty());
        assertEquals(ACTIVE, ticket.state());
        assertEquals(2, ticket.remainingTicks());
        assertTrue(slot.acquire(COMPETITOR, ANCHOR, 2).isEmpty());
        assertTrue(slot.acquire(OWNER, ANCHOR, 2).isEmpty());
        assertThrows(IllegalStateException.class, () -> slot.setUnleased(ANCHOR));
        assertThrows(IllegalStateException.class, slot::clearUnleased);
        assertThrows(IllegalStateException.class, slot::requireUnleased);
        slot.tick();
        assertEquals(ACTIVE, ticket.state());
        slot.tick();
        assertTerminal(ticket, EXPIRED);
        assertFalse(slot.occupied());
        assertTrue(slot.acquire(COMPETITOR, ANCHOR, 1).isPresent());
    }

    @Test
    void controllerIdIsALabelAndCannotAcquireAnOccupiedSlotEvenForTheSameOwner() {
        var slot = new NativeStructureControlSlot();
        var first = slot.acquire(OWNER, ANCHOR, 4).orElseThrow();
        assertEquals(OWNER, first.controllerId());
        assertTrue(slot.acquire(OWNER, FORCE, 200).isEmpty());
        assertTrue(slot.acquire(COMPETITOR, FORCE, 200).isEmpty());
        assertEquals(ANCHOR, first.control().orElseThrow());
        assertEquals(4, first.remainingTicks());
    }

    @Test
    void legacyCommandBlocksAcquisitionUntilExplicitClearOrForceConsumption() {
        var slot = new NativeStructureControlSlot();
        slot.setUnleased(ANCHOR);
        assertTrue(slot.acquire(OWNER, FORCE, 2).isEmpty());
        slot.tick();
        slot.consumeForce();
        assertEquals(ANCHOR, slot.control().orElseThrow());
        assertTrue(slot.clearUnleased());
        assertFalse(slot.clearUnleased());
        slot.setUnleased(FORCE);
        assertTrue(slot.acquire(OWNER, ANCHOR, 2).isEmpty());
        slot.consumeForce();
        assertTrue(slot.acquire(OWNER, ANCHOR, 2).isPresent());
    }

    @Test
    void unleasedReplacementKeepsLastWriteAndInvalidReplacementKeepsPreviousCommand() {
        var slot = new NativeStructureControlSlot();
        slot.setUnleased(ANCHOR);
        slot.setUnleased(FORCE);
        assertEquals(FORCE, slot.control().orElseThrow());
        assertThrows(NullPointerException.class, () -> slot.setUnleased(null));
        assertEquals(FORCE, slot.control().orElseThrow());
        assertDoesNotThrow(slot::requireUnleased);
        slot.consumeForce();
        assertFalse(slot.clearUnleased());
    }

    @Test
    void oneTickLeaseExpiresAtFirstTickEndAndNeverUnderflows() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, ANCHOR, 1).orElseThrow();
        assertEquals(ACTIVE, ticket.state());
        assertEquals(1, ticket.remainingTicks());
        slot.tick();
        assertTerminal(ticket, EXPIRED);
        assertTrue(slot.control().isEmpty());
        for (int i = 0; i < 4; i++) slot.tick();
        assertTerminal(ticket, EXPIRED);
        assertDoesNotThrow(slot::requireUnleased);
    }

    @Test
    void maximumDurationCountsServerTicksAndAnchorConsumptionDoesNotChangeDeadline() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, ANCHOR, StructurePhysicsLease.MAX_DURATION_TICKS).orElseThrow();
        for (int i = 1; i < StructurePhysicsLease.MAX_DURATION_TICKS; i++) {
            slot.consumeForce();
            slot.tick();
            assertEquals(ACTIVE, ticket.state());
            assertEquals(StructurePhysicsLease.MAX_DURATION_TICKS - i, ticket.remainingTicks());
            assertEquals(ANCHOR, ticket.control().orElseThrow());
        }
        slot.tick();
        assertTerminal(ticket, EXPIRED);
    }

    @Test
    void replacingTheTargetOrConsumedForceNeverImplicitlyRenews() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, ANCHOR, 2).orElseThrow();
        slot.tick();
        ticket.setControl(FORCE);
        assertEquals(1, ticket.remainingTicks());
        assertEquals(FORCE, slot.control().orElseThrow());
        slot.consumeForce();
        ticket.setControl(ANCHOR);
        assertEquals(1, ticket.remainingTicks());
        slot.tick();
        assertTerminal(ticket, EXPIRED);
        assertTrue(slot.control().isEmpty());
    }

    @Test
    void renewalResetsRatherThanAddsDurationAndDoesNotReplayConsumedForce() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, FORCE, 3).orElseThrow();
        slot.consumeForce();
        slot.tick();
        ticket.renew(4);
        assertEquals(4, ticket.remainingTicks());
        assertTrue(ticket.control().isEmpty());
        slot.tick();
        ticket.renew(1);
        assertEquals(1, ticket.remainingTicks());
        assertTrue(slot.acquire(COMPETITOR, ANCHOR, 1).isEmpty());
        slot.tick();
        assertTerminal(ticket, EXPIRED);
    }

    @Test
    void invalidRenewalAndControlLeaveCurrentCommandAndDeadlineUntouched() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, ANCHOR, 9).orElseThrow();
        slot.tick();
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 201, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> ticket.renew(invalid));
            assertEquals(8, ticket.remainingTicks());
            assertEquals(ANCHOR, ticket.control().orElseThrow());
        }
        assertThrows(NullPointerException.class, () -> ticket.setControl(null));
        assertEquals(8, ticket.remainingTicks());
        assertEquals(ANCHOR, slot.control().orElseThrow());
        assertEquals(ACTIVE, ticket.state());
    }

    @Test
    void invalidAcquisitionIsRejectedBeforeOccupancyWithoutMutatingExistingLease() {
        var slot = new NativeStructureControlSlot();
        var ticket = slot.acquire(OWNER, ANCHOR, 7).orElseThrow();
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 201, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> slot.acquire(COMPETITOR, FORCE, invalid));
        }
        assertThrows(NullPointerException.class, () -> slot.acquire(null, FORCE, 1));
        assertThrows(NullPointerException.class, () -> slot.acquire(COMPETITOR, null, 1));
        assertEquals(ACTIVE, ticket.state());
        assertEquals(7, ticket.remainingTicks());
        assertEquals(ANCHOR, ticket.control().orElseThrow());
        ticket.close();
        assertThrows(IllegalArgumentException.class, () -> slot.acquire(OWNER, FORCE, 0));
        assertThrows(NullPointerException.class, () -> slot.acquire(null, FORCE, 1));
        assertThrows(NullPointerException.class, () -> slot.acquire(OWNER, null, 1));
        assertTrue(slot.control().isEmpty());
        assertTrue(slot.acquire(COMPETITOR, FORCE, 1).isPresent());
    }

    @Test
    void releaseClearsOnlyItsOwnLeaseAndOldHandleCannotControlSameIdReplacement() {
        var slot = new NativeStructureControlSlot();
        var first = slot.acquire(OWNER, ANCHOR, 20).orElseThrow();
        first.close();
        assertTerminal(first, RELEASED);
        assertTrue(slot.control().isEmpty());
        var replacement = slot.acquire(OWNER, FORCE, 4).orElseThrow();
        assertNotSame(first, replacement);
        first.close();
        assertThrows(IllegalStateException.class, () -> first.setControl(ANCHOR));
        assertThrows(IllegalStateException.class, () -> first.renew(200));
        assertTerminal(first, RELEASED);
        assertEquals(ACTIVE, replacement.state());
        assertEquals(4, replacement.remainingTicks());
        assertEquals(FORCE, replacement.control().orElseThrow());
        assertEquals(FORCE, slot.control().orElseThrow());
    }

    @Test
    void expiredHandleCannotRenewOrClearANewerOwnersCommand() {
        var slot = new NativeStructureControlSlot();
        var expired = slot.acquire(OWNER, FORCE, 1).orElseThrow();
        slot.tick();
        var replacement = slot.acquire(COMPETITOR, ANCHOR, 10).orElseThrow();
        expired.close();
        assertThrows(IllegalStateException.class, () -> expired.setControl(FORCE));
        assertThrows(IllegalStateException.class, () -> expired.renew(1));
        assertTerminal(expired, EXPIRED);
        assertEquals(ANCHOR, slot.control().orElseThrow());
        assertEquals(10, replacement.remainingTicks());
    }

    @Test
    void invalidationTerminatesEvenAnEmptyLeasedSlotAndOldHandleCannotRevive() {
        var slot = new NativeStructureControlSlot();
        var invalidated = slot.acquire(OWNER, FORCE, 100).orElseThrow();
        slot.consumeForce();
        slot.invalidate();
        assertTerminal(invalidated, INVALIDATED);
        assertFalse(slot.clearUnleased());
        var replacement = slot.acquire(COMPETITOR, ANCHOR, 5).orElseThrow();
        invalidated.close();
        assertThrows(IllegalStateException.class, () -> invalidated.setControl(FORCE));
        assertThrows(IllegalStateException.class, () -> invalidated.renew(1));
        assertEquals(ANCHOR, replacement.control().orElseThrow());
        assertEquals(5, replacement.remainingTicks());
        slot.invalidate();
        assertTerminal(replacement, INVALIDATED);
        assertTerminal(invalidated, INVALIDATED);
        assertTrue(slot.control().isEmpty());
    }

    @Test
    void invalidationClearsLegacyControlsAndRepeatedCleanupDoesNotChangeTerminalReason() {
        var slot = new NativeStructureControlSlot();
        slot.setUnleased(ANCHOR);
        slot.invalidate();
        assertTrue(slot.control().isEmpty());
        var released = slot.acquire(OWNER, ANCHOR, 2).orElseThrow();
        released.close();
        slot.invalidate();
        slot.tick();
        released.close();
        assertTerminal(released, RELEASED);
        var expired = slot.acquire(OWNER, FORCE, 1).orElseThrow();
        slot.tick();
        slot.invalidate();
        expired.close();
        assertTerminal(expired, EXPIRED);
        slot.setUnleased(ANCHOR);
        assertEquals(ANCHOR, slot.control().orElseThrow());
        assertTrue(slot.clearUnleased());
    }

    private static void assertTerminal(NativeStructureControlSlot.Ticket ticket,
                                       StructurePhysicsLease.State expected) {
        assertEquals(expected, ticket.state());
        assertEquals(0, ticket.remainingTicks());
        assertTrue(ticket.control().isEmpty());
    }
}
