package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelStructureRefsTest {
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @AfterEach
    void clearRegistry() {
        SubLevelActivationRegistry.clear();
    }

    @Test
    void mapsSubLevelIdentityToSyntheticReference() {
        var id = SubLevelId.random();
        var subLevel = new SubLevel(id, new PlotPos(0, 0), new AABB(0, 0, 0, 2, 2, 2),
                StructurePose.identity(Vec3.ZERO));
        var reference = SubLevelStructureRefs.of(subLevel, OVERWORLD, 3);

        assertEquals(id.value(), reference.id());
        assertEquals(OVERWORLD, reference.dimension());
        assertEquals(SubLevelStructureRefs.BACKEND, reference.backend());
        assertEquals(3, reference.generation());
        assertTrue(SubLevelStructureRefs.isSubLevel(reference));
        assertEquals(id, SubLevelStructureRefs.id(reference).orElseThrow());
        assertEquals(id, SubLevelStructureRefs.id(reference, 3).orElseThrow());
        assertTrue(SubLevelStructureRefs.id(reference, 4).isEmpty());
    }

    @Test
    void rejectsForeignBackend() {
        var foreign = new org.academy.api.common.structure.StructureRef(java.util.UUID.randomUUID(),
                OVERWORLD, Identifier.fromNamespaceAndPath("academy", "native"), 1);
        assertFalse(SubLevelStructureRefs.isSubLevel(foreign));
        assertTrue(SubLevelStructureRefs.id(foreign).isEmpty());
    }

    @Test
    void activationAdvancesGenerationAndChangesRuntimeId() {
        var id = SubLevelId.random();
        var first = SubLevelActivationRegistry.activation(id);
        var second = SubLevelActivationRegistry.advance(id);

        assertEquals(1, first.generation());
        assertEquals(2, second.generation());
        assertFalse(first.runtimeId().equals(second.runtimeId()));
        assertTrue(SubLevelActivationRegistry.matches(id, second.runtimeId(), 2));
        assertFalse(SubLevelActivationRegistry.matches(id, first.runtimeId(), 1));
    }
}
