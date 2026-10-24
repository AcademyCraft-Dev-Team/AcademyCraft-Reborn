package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureLifecycleContractsTest {
    private static final Identifier POLICY = Identifier.fromNamespaceAndPath("academy", "marked_rest");

    @Test
    void policySnapshotCannotBeChangedAfterPreparation() {
        var parameters = new HashMap<String, Double>();
        parameters.put("rest_ticks", 100.0);
        var policy = new StructureProvenance.RestorePolicy(POLICY, 1, parameters);
        parameters.put("rest_ticks", 0.0);
        parameters.put("unexpected", 1.0);

        assertEquals(Map.of("rest_ticks", 100.0), policy.parameters());
        assertThrows(UnsupportedOperationException.class, () -> policy.parameters().put("rest_ticks", 0.0));
    }

    @Test
    void policyRejectsNonFiniteAndUnboundedPersistentInputs() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, StructureProvenance.MAX_PARAMETER_MAGNITUDE + 1.0}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StructureProvenance.RestorePolicy(POLICY, 1, Map.of("value", invalid)));
        }
        var tooMany = new HashMap<String, Double>();
        for (int index = 0; index <= StructureProvenance.MAX_POLICY_PARAMETERS; index++) {
            tooMany.put("parameter_" + index, 0.0);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new StructureProvenance.RestorePolicy(POLICY, 1, tooMany));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureProvenance.RestorePolicy(POLICY, 1, Map.of(" ", 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureProvenance.RestorePolicy(POLICY, 0, Map.of()));
    }

    @Test
    void sourceGridRotationNormalizesButRejectsInvalidOrientation() {
        assertEquals(new StructureProvenance.GridRotation(0, 0, 0, 1),
                new StructureProvenance.GridRotation(0, 0, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new StructureProvenance.GridRotation(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureProvenance.GridRotation(Double.NaN, 0, 0, 1));
    }

    @Test
    void fragmentMetadataRequiresAnExplicitParentAndCannotPrepareItsOwnParent() {
        var parent = reference();
        var metadata = metadata(StructureProvenance.CreationKind.SKILL_FRAGMENT, Optional.of(parent));
        assertThrows(IllegalArgumentException.class,
                () -> metadata(StructureProvenance.CreationKind.SKILL_FRAGMENT, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> metadata(StructureProvenance.CreationKind.SKILL_CAPTURE, Optional.of(parent)));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureProvenance.Preparation(parent, UUID.randomUUID(), 0, metadata));
        assertEquals(metadata, new StructureProvenance.Preparation(reference(), UUID.randomUUID(), 0,
                metadata).metadata());
    }

    @Test
    void restorationPreviewCopiesMutablePositionsAndMappingList() {
        var source = new BlockPos.MutableBlockPos(1, 2, 3);
        var target = new BlockPos.MutableBlockPos(-5, 64, 9);
        var placements = new ArrayList<StructureGridRestoration.Placement>();
        placements.add(new StructureGridRestoration.Placement(source, target));
        var preview = preview(placements);
        source.set(7, 8, 9);
        target.set(0, 0, 0);
        placements.clear();

        assertEquals(new BlockPos(1, 2, 3), preview.placements().getFirst().localPosition());
        assertEquals(new BlockPos(-5, 64, 9), preview.placements().getFirst().targetPosition());
        assertThrows(UnsupportedOperationException.class, () -> preview.placements().clear());
    }

    @Test
    void restorationMappingCannotDuplicateOwnershipOrExceedItsBudget() {
        var first = new StructureGridRestoration.Placement(BlockPos.ZERO, new BlockPos(0, 64, 0));
        var sameSource = new StructureGridRestoration.Placement(BlockPos.ZERO, new BlockPos(1, 64, 0));
        var sameTarget = new StructureGridRestoration.Placement(new BlockPos(1, 0, 0), first.targetPosition());
        assertThrows(IllegalArgumentException.class, () -> preview(List.of(first, sameSource)));
        assertThrows(IllegalArgumentException.class, () -> preview(List.of(first, sameTarget)));
        assertThrows(IllegalArgumentException.class, () -> preview(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> preview(Collections.nCopies(StructureGridRestoration.MAX_PREVIEW_CELLS + 1, first)));
    }

    @Test
    void holdsCannotDeclareUnboundedDurationAndIncompleteWorkCannotDeclareRestored() {
        var controller = UUID.randomUUID();
        var reason = StructureGridRestoration.HoldReason.PRECISION_CONTROL;
        assertThrows(IllegalArgumentException.class,
                () -> new StructureGridRestoration.HoldRequest(controller, reason, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureGridRestoration.HoldRequest(controller, reason,
                        StructureGridRestoration.MAX_HOLD_TICKS + 1));
        assertDoesNotThrow(() -> new StructureGridRestoration.HoldRequest(controller, reason,
                StructureGridRestoration.MAX_HOLD_TICKS));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureGridRestoration.Progress(UUID.randomUUID(), reference(),
                        StructureGridRestoration.Phase.RESTORED, 2, 1));
        assertDoesNotThrow(() -> new StructureGridRestoration.Progress(UUID.randomUUID(), reference(),
                StructureGridRestoration.Phase.RESTORED, 2, 2));
    }

    private static StructureProvenance.Metadata metadata(StructureProvenance.CreationKind source,
                                                         Optional<StructureRef> parent) {
        return new StructureProvenance.Metadata(source, UUID.randomUUID(), UUID.randomUUID(), parent,
                Optional.empty(), Identifier.fromNamespaceAndPath("academy", "kinetic_throw"),
                new StructureProvenance.GridRotation(0, 0, 0, 1),
                new StructureProvenance.RestorePolicy(POLICY, 1, Map.of()), 0);
    }

    private static StructureGridRestoration.Preview preview(List<StructureGridRestoration.Placement> placements) {
        return new StructureGridRestoration.Preview(UUID.randomUUID(), reference(), UUID.randomUUID(),
                0, 0, StructurePose.identity(Vec3.ZERO), StructurePose.identity(new Vec3(0, 64, 0)), placements);
    }

    private static StructureRef reference() {
        return new StructureRef(UUID.randomUUID(), Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                Identifier.fromNamespaceAndPath("academy", "sublevel"), 0);
    }
}
