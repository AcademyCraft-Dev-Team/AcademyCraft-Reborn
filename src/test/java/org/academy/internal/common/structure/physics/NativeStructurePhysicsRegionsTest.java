package org.academy.internal.common.structure.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysicsRegionInfo;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructurePhysicsRegionsTest {
    private static final Identifier HOST = Identifier.parse("minecraft:overworld");
    private static final UUID FIRST = new UUID(1, 1);
    private static final UUID SECOND = new UUID(1, 2);
    private static final AABB FIRST_BOUNDS = new AABB(0, 64, 0, 24, 88, 24);
    private static final AABB SECOND_BOUNDS = new AABB(25, 64, 0, 49, 88, 24);

    @Test
    void groupsInterleavedMembersByStableIdentityIncludingInactiveFailedCheckpoints() {
        var active = entry(1, FIRST, FIRST_BOUNDS, true, true);
        var other = entry(2, SECOND, SECOND_BOUNDS, false, true);
        var inactiveFailed = entry(3, FIRST, FIRST_BOUNDS, false, false);
        var expectedFirst = new StructurePhysicsRegionInfo(FIRST, FIRST_BOUNDS, 2, false);
        var expectedSecond = new StructurePhysicsRegionInfo(SECOND, SECOND_BOUNDS, 1, true);

        var descriptions = NativeStructurePhysics.describeSaved(List.of(active, other, inactiveFailed));
        assertEquals(List.of(expectedFirst, expectedSecond), descriptions);
        assertEquals(List.of(expectedSecond, expectedFirst),
                NativeStructurePhysics.describeSaved(List.of(other, inactiveFailed, active)));
        assertThrows(UnsupportedOperationException.class, descriptions::clear);
        assertTrue(active.physicsAttachment().resumeAllowed());
        assertFalse(inactiveFailed.active());
    }

    @Test
    void legacySelectionRejectsAmbiguityWithoutSelectingOrChangingARegion() {
        assertTrue(NativeStructurePhysics.soleSavedRegion(List.of()).isEmpty());
        var first = new StructurePhysicsRegionInfo(FIRST, FIRST_BOUNDS, 1, true);
        assertEquals(first, NativeStructurePhysics.soleSavedRegion(List.of(first)).orElseThrow());
        var descriptions = NativeStructurePhysics.describeSaved(List.of(
                entry(1, FIRST, FIRST_BOUNDS, true, true), entry(2, SECOND, SECOND_BOUNDS, true, true)));
        var snapshot = List.copyOf(descriptions);

        assertThrows(IllegalStateException.class, () -> NativeStructurePhysics.soleSavedRegion(descriptions));
        assertEquals(snapshot, descriptions);
        assertEquals(List.of(FIRST, SECOND), descriptions.stream().map(StructurePhysicsRegionInfo::id).toList());
    }

    @Test
    void descriptionsRejectConflictingIdentityInsteadOfHidingItByAggregation() {
        var original = entry(1, FIRST, FIRST_BOUNDS, true, true);
        var changedBounds = entry(2, FIRST, SECOND_BOUNDS, false, true);
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructurePhysics.describeSaved(List.of(original, changedBounds)));
        var otherHost = new NativeStructureManifest.Entry(new StructureRef(new UUID(0, 3),
                Identifier.parse("minecraft:the_nether"), original.reference().backend(), 0), 2,
                original.pose(), false, false, original.physicsAttachment());
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructurePhysics.describeSaved(List.of(original, otherHost)));
    }

    private static NativeStructureManifest.Entry entry(int owner, UUID region, AABB bounds,
                                                      boolean active, boolean resumeAllowed) {
        return new NativeStructureManifest.Entry(new StructureRef(new UUID(0, owner), HOST,
                Identifier.parse("academy:native_level"), 0), owner - 1,
                StructurePose.identity(Vec3.ZERO), active, false,
                new NativeStructurePhysicsAttachment(region, bounds, null, resumeAllowed));
    }
}
