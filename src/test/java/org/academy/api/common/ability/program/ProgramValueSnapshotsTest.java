package org.academy.api.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramValueSnapshotsTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void allSupportedValueDomainsHaveAConcreteSnapshotRepresentation() {
        var identity = new Object();
        var structure = structure();
        var position = new ProgramWorldPosition(OVERWORLD, 1.5, 64, 2.5);
        var block = new ProgramBlockPosition(OVERWORLD, 1, 64, 2);
        var direction = new ProgramDirection(0, 1, 0);
        var values = Map.<ProgramValueType, Object>ofEntries(
                Map.entry(ProgramValueTypes.BOOLEAN, true),
                Map.entry(ProgramValueTypes.INTEGER, 4),
                Map.entry(ProgramValueTypes.BIG_INTEGER, BigInteger.TEN),
                Map.entry(ProgramValueTypes.FLOAT, 1.25),
                Map.entry(ProgramValueTypes.TEXT, "sample"),
                Map.entry(ProgramValueTypes.TAG, new ProgramTag(Identifier.parse("minecraft:logs"))),
                Map.entry(ProgramValueTypes.IDENTIFIER, OVERWORLD),
                Map.entry(ProgramValueTypes.VECTOR, new ProgramVector(1, 0, 0)),
                Map.entry(ProgramValueTypes.DIRECTION, direction),
                Map.entry(ProgramValueTypes.WORLD_POSITION, position),
                Map.entry(ProgramValueTypes.BLOCK_POSITION, block),
                Map.entry(ProgramValueTypes.ENTITY_REFERENCE, identity),
                Map.entry(ProgramValueTypes.LIVING_ENTITY_REFERENCE, identity),
                Map.entry(ProgramValueTypes.STRUCTURE_REFERENCE, structure),
                Map.entry(ProgramValueTypes.STRUCTURE_SET, List.of(structure)),
                Map.entry(ProgramValueTypes.STRUCTURE_ANCHOR, new ProgramStructureAnchor(structure, new BlockPos(1, 2, 3))),
                Map.entry(ProgramValueTypes.STRUCTURE_POSE, new ProgramStructurePose(direction, Math.PI / 2)),
                Map.entry(ProgramValueTypes.DIRECTION_SET, List.of(direction)),
                Map.entry(ProgramValueTypes.WORLD_POSITION_SET, List.of(position)),
                Map.entry(ProgramValueTypes.BLOCK_POSITION_SET, List.of(block)),
                Map.entry(ProgramValueTypes.ENTITY_SET, List.of(identity)),
                Map.entry(ProgramValueTypes.LIVING_ENTITY_SET, List.of(identity))
        );
        assertEquals(22, ProgramValueSnapshots.supportedTypes().size());
        assertEquals(values.keySet(), java.util.Set.copyOf(ProgramValueSnapshots.supportedTypes()));
        values.forEach((type, raw) -> {
            var snapshot = ProgramValueSnapshots.snapshot(new ProgramValue<>(type, raw));
            assertEquals(type, snapshot.type());
            assertEquals(raw, snapshot.value());
            assertSame(type, ProgramValueSnapshots.requireType(type.id()));
        });
        assertThrows(UnsupportedOperationException.class,
                () -> ProgramValueSnapshots.supportedTypes().clear());
    }

    @Test
    void mutableCollectionMembershipIsCopiedWithoutSortingOrDeduplication() {
        var first = new Object();
        var second = new Object();
        var source = new ArrayList<>(List.of(first, second, first));
        var snapshot = ProgramValueSnapshots.snapshot(new ProgramValue<>(ProgramValueTypes.ENTITY_SET, source));
        source.clear();
        assertEquals(List.of(first, second, first), snapshot.value());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) snapshot.value()).clear());
        assertEquals(List.of(), ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.STRUCTURE_SET, new ArrayList<>())).value());
    }

    @Test
    void referencesKeepIdentityAndDoNotPretendToFreezeLiveProperties() {
        var liveReference = new AtomicInteger(1);
        var snapshot = ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.ENTITY_REFERENCE, liveReference));
        liveReference.set(2);
        assertSame(liveReference, snapshot.value());
        assertEquals(2, ((AtomicInteger) snapshot.value()).get());
        var structure = structure();
        assertSame(structure, ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.STRUCTURE_REFERENCE, structure)).value());
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.ENTITY_REFERENCE, liveReference), ProgramValueTypes.WORLD_POSITION));
    }

    @Test
    void numericSnapshotsNormalizeMutableNumbersAndRejectInexactOrNonFiniteValues() {
        var source = new AtomicInteger(7);
        var snapshot = ProgramValueSnapshots.snapshot(new ProgramValue<>(ProgramValueTypes.INTEGER, source));
        source.set(99);
        assertInstanceOf(Integer.class, snapshot.value());
        assertEquals(7, snapshot.value());
        assertEquals(7.0, ProgramValueSnapshots.snapshot(snapshot, ProgramValueTypes.FLOAT).value());
        assertEquals(BigInteger.valueOf(99), ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.BIG_INTEGER, source)).value());
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.INTEGER, 1.5)));
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.INTEGER, Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.FLOAT, Double.NaN)));
    }

    @Test
    void onlyExistingCompatibleValueConversionsAreAllowed() {
        var tag = new ProgramTag(Identifier.parse("minecraft:logs"));
        assertEquals("#minecraft:logs", ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.TAG, tag), ProgramValueTypes.TEXT).value());
        var identity = new Object();
        assertSame(identity, ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.LIVING_ENTITY_REFERENCE, identity),
                ProgramValueTypes.ENTITY_REFERENCE).value());
        assertEquals(List.of(identity), ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.LIVING_ENTITY_SET, List.of(identity)),
                ProgramValueTypes.ENTITY_SET).value());
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.FLOAT, 1.0), ProgramValueTypes.INTEGER));
    }

    @Test
    void leasesReceiptsFlowAndUndefinedRepresentationsCannotBeSampled() {
        for (var type : List.of(ProgramValueTypes.FLOW, ProgramValueTypes.DURATION,
                ProgramValueTypes.CONTROL_DESTINATION, ProgramValueTypes.ACTION_RESULT,
                ProgramValueTypes.STRUCTURE_CONTROL, ProgramValueTypes.STRUCTURE_OPERATION_RESULT,
                new ProgramValueType(Identifier.parse("example:custom_value")))) {
            assertFalse(ProgramValueSnapshots.supports(type));
            assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.requireType(type.id()));
            assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(new ProgramValue<>(type, 1)));
        }
    }

    @Test
    void malformedRawValuesAndForeignCollectionMembersFailAtTheSampleNode() {
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.INTEGER, "1")));
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.TEXT, new StringBuilder("mutable"))));
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.WORLD_POSITION_SET, List.of("foreign"))));
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.STRUCTURE_REFERENCE, new Object())));
        var nullMember = new ArrayList<>();
        nullMember.add(null);
        assertThrows(IllegalArgumentException.class, () -> ProgramValueSnapshots.snapshot(
                new ProgramValue<>(ProgramValueTypes.ENTITY_SET, nullMember)));
    }

    private static ProgramStructureReference structure() {
        return new ProgramStructureReference(new StructureRef(UUID.randomUUID(), OVERWORLD,
                Identifier.parse("academy:native"), 0), UUID.randomUUID());
    }
}
