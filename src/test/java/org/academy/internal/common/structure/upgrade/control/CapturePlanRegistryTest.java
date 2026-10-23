package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturePlanRegistryTest {
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "sublevel");
    private static final long NOW = 1_000;

    @Test
    void previewsAreOnlyVisibleToTheirOwningActorAndExpire() {
        var registry = new CapturePlanRegistry();
        var actor = UUID.randomUUID();
        var plan = preview(actor, UUID.randomUUID(), 200);
        assertEquals(CapturePlanRegistry.Registration.REGISTERED, registry.register(plan, source(), NOW));

        assertEquals(CapturePlanRegistry.Outcome.READY, registry.resolve(plan.planId(), actor, NOW).outcome());
        assertEquals(CapturePlanRegistry.Outcome.DENIED,
                registry.resolve(plan.planId(), UUID.randomUUID(), NOW).outcome());
        assertEquals(CapturePlanRegistry.Outcome.MISSING,
                registry.resolve(UUID.randomUUID(), actor, NOW).outcome());
        assertEquals(CapturePlanRegistry.Outcome.EXPIRED,
                registry.resolve(plan.planId(), actor, NOW + 201).outcome());
        assertEquals(0, registry.size());
    }

    @Test
    void onePreviewBindsExactlyOneCaptureAndRepeatsTheFirstBinding() {
        var registry = new CapturePlanRegistry();
        var actor = UUID.randomUUID();
        var control = UUID.randomUUID();
        var capture = UUID.randomUUID();
        var plan = preview(actor, control, 200);
        registry.register(plan, source(), NOW);
        var bound = boundPlan(plan.planId(), control, NOW + 400);

        var first = registry.bind(actor, bound, capture, NOW);
        assertEquals(CapturePlanRegistry.Outcome.READY, first.outcome());
        assertSame(bound, first.plan());
        var repeated = registry.bind(actor, boundPlan(plan.planId(), control, NOW + 400), capture, NOW + 1);
        assertEquals(CapturePlanRegistry.Outcome.REPEATED, repeated.outcome());
        assertSame(bound, repeated.plan(), "A repeated retry must return the stored plan, not a new one");
        assertEquals(CapturePlanRegistry.Outcome.CONFLICT,
                registry.bind(actor, bound, UUID.randomUUID(), NOW + 2).outcome());
        assertTrue(registry.resolve(plan.planId(), actor, NOW).entry().bound());
    }

    @Test
    void foreignActorsAndOtherControlTokensCannotBind() {
        var registry = new CapturePlanRegistry();
        var actor = UUID.randomUUID();
        var control = UUID.randomUUID();
        var plan = preview(actor, control, 200);
        registry.register(plan, source(), NOW);

        assertEquals(CapturePlanRegistry.Outcome.DENIED,
                registry.bind(UUID.randomUUID(), boundPlan(plan.planId(), control, NOW + 400), UUID.randomUUID(), NOW)
                        .outcome());
        assertEquals(CapturePlanRegistry.Outcome.DENIED,
                registry.bind(actor, boundPlan(plan.planId(), UUID.randomUUID(), NOW + 400), UUID.randomUUID(), NOW)
                        .outcome(), "A binding must keep the previewed control token");
        assertFalse(registry.resolve(plan.planId(), actor, NOW).entry().bound());
    }

    @Test
    void capacityIsReportedInsteadOfEvictingLivePreviews() {
        var registry = new CapturePlanRegistry();
        var actor = UUID.randomUUID();
        for (int index = 0; index < CapturePlanRegistry.MAX_ENTRIES; index++) {
            assertEquals(CapturePlanRegistry.Registration.REGISTERED,
                    registry.register(preview(actor, UUID.randomUUID(), 400), source(), NOW));
        }
        assertEquals(CapturePlanRegistry.Registration.CAPACITY,
                registry.register(preview(actor, UUID.randomUUID(), 400), source(), NOW));
        assertEquals(CapturePlanRegistry.MAX_ENTRIES, registry.size());
    }

    @Test
    void aPreviewCannotCarryADigestFromAnotherSource() {
        var registry = new CapturePlanRegistry();
        var plan = preview(UUID.randomUUID(), UUID.randomUUID(), 200);
        var other = CaptureSourceSnapshot.of(List.of(
                new CaptureSourceSnapshot.Cell(new BlockPos(9, 9, 9), "minecraft:stone")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(plan, other, NOW));
        assertEquals(0, registry.size());
    }

    private static CaptureSourceSnapshot source() {
        return CaptureSourceSnapshot.of(List.of(
                new CaptureSourceSnapshot.Cell(BlockPos.ZERO, "minecraft:stone"),
                new CaptureSourceSnapshot.Cell(new BlockPos(0, 1, 0), "minecraft:dirt")));
    }

    private static StructureExtraction.CapturePlan preview(UUID actor, UUID control, long lifetimeTicks) {
        return new StructureExtraction.CapturePlan(UUID.randomUUID(), actor, DIMENSION, source().digest(), 0,
                NOW + lifetimeTicks, captureRequest(control), List.of(Vec3.ZERO, new Vec3(0, 5, 0)), List.of());
    }

    private static StructureExtraction.CaptureRequest captureRequest(UUID control) {
        var capture = new StructureCaptureRequest(List.of(BlockPos.ZERO, new BlockPos(0, 1, 0)),
                StructureCaptureRequest.MAXIMUM_BLOCKS, StructureCaptureRequest.MAXIMUM_SPAN,
                StructureCaptureRequest.ALLOW, true);
        return new StructureExtraction.CaptureRequest(control, capture, 5.0, 200, 200,
                new StructureExtraction.Limits(50.0, 500.0, 5_000.0, 1.0, 50_000));
    }

    private static StructureExtraction.Plan boundPlan(UUID planId, UUID control, long expiresAt) {
        var reference = new StructureRef(UUID.randomUUID(), DIMENSION, BACKEND, 1);
        var request = new StructureExtraction.PlanRequest(control, StructureExtraction.SourceKind.NEW_CAPTURE,
                StructurePose.identity(Vec3.ZERO), new Vec3(0.5, 0.5, 0.5), 5.0, 200, 200,
                new StructureExtraction.Limits(50.0, 500.0, 5_000.0, 1.0, 50_000));
        return new StructureExtraction.Plan(planId, reference, UUID.randomUUID(), 1, 1, 0, expiresAt, request,
                List.of(Vec3.ZERO, new Vec3(0, 5, 0)), List.of());
    }
}
