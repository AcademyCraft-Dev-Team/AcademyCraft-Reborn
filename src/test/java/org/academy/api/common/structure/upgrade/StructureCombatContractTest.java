package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureCombatContractTest {
    @Test void damageAuthorityMustNameAnActiveActorOrAuthenticatedLaunchClaim() {
        assertThrows(NullPointerException.class, () -> new StructureDamage.ActiveActor(null));
        assertThrows(NullPointerException.class, () -> new StructureDamage.Launched(null));
    }

    @Test void unsupportedCannotPretendToOwnAnOperationOrResult() {
        assertThrows(IllegalArgumentException.class, () -> new StructureOperation.Result<>(
                StructureOperation.Status.UNSUPPORTED, "disabled", Optional.of(UUID.randomUUID()), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureOperation.Result<>(
                StructureOperation.Status.RUNNING, "pending", Optional.empty(), Optional.empty()));
    }

    @Test void fractureCannotAssignTheSameMutableCellToTwoChildren() {
        var mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        var first = new StructureFracture.Fragment(List.of(mutable));
        mutable.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), first.cells().getFirst());
        var second = new StructureFracture.Fragment(List.of(new BlockPos(1, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> new StructureFracture.Plan(
                UUID.randomUUID(), UUID.randomUUID(), List.of(first, second)));
    }

    @Test void damageCannotExceedScansOrPublishAmbiguousCellAddresses() {
        assertThrows(IllegalArgumentException.class, () -> new StructureDamage.Request(
                UUID.randomUUID(), Vec3.ZERO, 3, 1, 10, 11, StructureDamage.DropPolicy.NO_DROPS));
        assertThrows(IllegalArgumentException.class, () -> new StructureDamage.Cell(
                Optional.empty(), Optional.empty(), 0, "state"));
    }

    @Test void asynchronousDamageQueryMustReturnTheSameEffectsPreparedPlan() {
        var operationId = UUID.randomUUID();
        var effectId = UUID.randomUUID();
        var request = new StructureDamage.Request(effectId, Vec3.ZERO, 3, 10,
                10, 5, StructureDamage.DropPolicy.EXISTING_SKILL_RULES);
        var batch = new StructureDamage.Batch(UUID.randomUUID(), request, List.of());
        var emptyReceipt = new StructureDamage.Receipt(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new StructureDamage.Progress(
                operationId, effectId, StructureDamage.Phase.READY, emptyReceipt, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureDamage.Progress(
                operationId, UUID.randomUUID(), StructureDamage.Phase.READY, emptyReceipt, Optional.of(batch)));
        assertEquals(batch, new StructureDamage.Progress(operationId, effectId, StructureDamage.Phase.READY,
                emptyReceipt, Optional.of(batch)).preparedBatch().orElseThrow());
        assertDoesNotThrow(() -> new StructureDamage.Progress(operationId, effectId, StructureDamage.Phase.CANCELLED,
                new StructureDamage.Receipt(2, 0, 3), Optional.empty()));
    }

    @Test void fractureProgressCannotReturnAnotherPlansReservationOrClaimCompletionWithoutReceipt() {
        var operationId = UUID.randomUUID();
        var planId = UUID.randomUUID();
        var plan = new StructureFracture.Plan(planId, UUID.randomUUID(),
                List.of(new StructureFracture.Fragment(List.of(BlockPos.ZERO))));
        var reservation = new StructureFracture.Reservation(operationId, planId);
        assertThrows(IllegalArgumentException.class, () -> new StructureFracture.Progress(
                operationId, planId, StructureFracture.Phase.READY,
                Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureFracture.Progress(
                operationId, planId, StructureFracture.Phase.RESERVED,
                Optional.empty(), Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureFracture.Progress(
                UUID.randomUUID(), planId, StructureFracture.Phase.RESERVED,
                Optional.of(plan), Optional.of(reservation), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureFracture.Progress(
                operationId, planId, StructureFracture.Phase.COMPLETED,
                Optional.empty(), Optional.empty(), Optional.empty()));
        assertEquals(plan, new StructureFracture.Progress(operationId, planId, StructureFracture.Phase.READY,
                Optional.of(plan), Optional.empty(), Optional.empty()).preparedPlan().orElseThrow());
        assertEquals(reservation, new StructureFracture.Progress(operationId, planId, StructureFracture.Phase.RESERVED,
                Optional.empty(), Optional.of(reservation), Optional.empty()).reservation().orElseThrow());
    }

    @Test void contactTimingRetainsPrecisionAndRejectsInvalidFractions() {
        var precise = contact(0.125, StructureImpactFeed.TimingPrecision.STEP_FRACTION);
        var coarse = contact(0.0, StructureImpactFeed.TimingPrecision.SUBSTEP_ONLY);
        assertEquals(0.125, precise.timeFraction());
        assertEquals(StructureImpactFeed.TimingPrecision.SUBSTEP_ONLY, coarse.timingPrecision());
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01}) {
            assertThrows(IllegalArgumentException.class,
                    () -> contact(invalid, StructureImpactFeed.TimingPrecision.STEP_FRACTION));
        }
    }

    @Test void boundedContactPagesCanResumeWithinTheSameStepWithoutAliasingTheirContents() {
        var source = new ArrayList<StructureImpactFeed.Contact>();
        source.add(contact(0.1, StructureImpactFeed.TimingPrecision.STEP_FRACTION));
        var first = new StructureImpactFeed.Batch(1, 5, false, source);
        source.clear();
        var cursor = new StructureImpactFeed.Cursor(UUID.randomUUID(), first.nextCursor(), 1);
        var second = new StructureImpactFeed.Batch(cursor.afterCursor() + 1, 5, false,
                List.of(contact(0.2, StructureImpactFeed.TimingPrecision.STEP_FRACTION)));
        assertEquals(1, first.reports().size());
        assertEquals(first.throughStep(), second.throughStep());
        assertTrue(second.nextCursor() > first.nextCursor());
        assertThrows(UnsupportedOperationException.class, () -> first.reports().clear());
        assertDoesNotThrow(() -> new StructureImpactFeed.Batch(0, -1, false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureImpactFeed.Batch(1, 4, false, first.reports()));
    }

    private static StructureImpactFeed.Contact contact(double timeFraction,
                                                       StructureImpactFeed.TimingPrecision precision) {
        var reference = new StructureRef(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                Identifier.fromNamespaceAndPath("academy", "sublevel"), 0);
        return new StructureImpactFeed.Contact(reference, UUID.randomUUID(), 0, 5, 0,
                timeFraction, precision, "minecraft:overworld/terrain/0,0,0", Vec3.ZERO,
                new Vec3(0, 1, 0), 10, 5);
    }
}
