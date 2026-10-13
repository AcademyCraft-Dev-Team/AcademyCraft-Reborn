package org.academy.internal.common.structure.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePhysicsRegionLayoutTest {
    private static final Identifier HOST = Identifier.parse("minecraft:overworld");
    private static final AABB BOUNDS = new AABB(0, 64, 0, 24, 88, 24);

    @Test
    void repeatedMembersAndIndependentHostsShareOnlyTheDistinctRegionBudget() {
        var first = region(1, HOST, BOUNDS);
        var separated = region(2, HOST, BOUNDS.move(25, 0, 0));
        var otherHost = region(3, Identifier.parse("minecraft:the_nether"), BOUNDS);
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(
                List.of(first, first, separated, otherHost, separated, first)));
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(
                List.of(otherHost, separated, first)));
        var fourth = region(4, Identifier.parse("test:fourth"), BOUNDS);
        assertThrows(IllegalArgumentException.class, () -> NativeStructurePhysicsRegionLayout.validate(
                List.of(first, separated, otherHost, fourth)));
    }

    @Test
    void exactOneBlockGapIsAcceptedOnEitherSideOfEachAxis() {
        var first = region(1, HOST, BOUNDS);
        for (var offset : new double[][]{{25, 0, 0}, {-25, 0, 0}, {0, 25, 0},
                {0, -25, 0}, {0, 0, 25}, {0, 0, -25}}) {
            var other = region(2, HOST, BOUNDS.move(offset[0], offset[1], offset[2]));
            assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(first, other)));
            assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(other, first)));
        }
    }

    @Test
    void overlapTouchingAndSubBlockGapsCannotCreateUnsolvedCrossRegionContacts() {
        var first = region(1, HOST, BOUNDS);
        for (var offset : new double[][]{{0, 0, 0}, {23, 0, 0}, {24, 0, 0}, {24.999, 0, 0},
                {-24.999, 0, 0}, {0, 24.999, 0}, {0, -24.999, 0}, {0, 0, 24.999}, {0, 0, -24.999},
                {24.75, 24.75, 24.75}}) {
            var other = region(2, HOST, BOUNDS.move(offset[0], offset[1], offset[2]));
            assertThrows(IllegalArgumentException.class, () -> NativeStructurePhysicsRegionLayout.validate(List.of(first, other)));
        }
    }

    @Test
    void oneIdentityCannotAliasDifferentBoundsOrHosts() {
        var first = region(1, HOST, BOUNDS);
        for (var conflict : List.of(region(1, HOST, BOUNDS.move(25, 0, 0)),
                region(1, Identifier.parse("minecraft:the_nether"), BOUNDS))) {
            assertThrows(IllegalArgumentException.class, () -> NativeStructurePhysicsRegionLayout.validate(List.of(first, conflict)));
        }
    }

    @Test
    void legacyIdentityUsesOnlyCanonicalHostAndBounds() {
        var bounds = new AABB(0, 64, 0, 1, 65, 1);
        var negativeZero = new AABB(-0.0, 64, -0.0, 1, 65, 1);
        var expected = NativeStructurePhysicsRegionLayout.legacyId(HOST, bounds);
        assertEquals(expected, NativeStructurePhysicsRegionLayout.legacyId(Identifier.parse("minecraft:overworld"), bounds));
        assertEquals(expected, NativeStructurePhysicsRegionLayout.legacyId(HOST, negativeZero));
        assertNotEquals(expected, NativeStructurePhysicsRegionLayout.legacyId(HOST, bounds.move(0.125, 0, 0)));
        assertNotEquals(expected, NativeStructurePhysicsRegionLayout.legacyId(Identifier.parse("minecraft:the_nether"), bounds));
    }

    @Test
    void malformedValuesFailBeforeLayoutOrMigrationCanAuthorizeThem() {
        assertThrows(NullPointerException.class, () -> new NativeStructurePhysicsRegionLayout.Region(null, HOST, BOUNDS));
        assertThrows(NullPointerException.class, () -> region(1, null, BOUNDS));
        assertThrows(NullPointerException.class, () -> NativeStructurePhysicsRegionLayout.validate(null));
        for (var bounds : List.of(new AABB(0, 0, 0, 0, 1, 1), new AABB(0, 0, 0, 24.1, 1, 1),
                new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1))) {
            assertThrows(IllegalArgumentException.class, () -> region(1, HOST, bounds));
            assertThrows(IllegalArgumentException.class, () -> NativeStructurePhysicsRegionLayout.legacyId(HOST, bounds));
        }
    }

    private static NativeStructurePhysicsRegionLayout.Region region(long id, Identifier host, AABB bounds) {
        return new NativeStructurePhysicsRegionLayout.Region(new UUID(0, id), host, bounds);
    }
}
