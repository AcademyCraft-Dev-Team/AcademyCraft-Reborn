package org.academy.internal.common.sublevel;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubLevelTerrainBreakReferenceTest {
    private static final Identifier HOST = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void hostTerrainSweepAcceptsBothBackendIdentities() {
        for (var backend : new String[]{"sublevel", "sable"}) {
            var reference = new StructureRef(UUID.randomUUID(), HOST,
                    Identifier.fromNamespaceAndPath("academy", backend), 1);
            assertDoesNotThrow(() -> SubLevelTerrainBreakPolicy.requireReference(HOST, reference));
        }
    }

    @Test
    void missingOrForeignHostIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SubLevelTerrainBreakPolicy.requireReference(HOST, null));
        var reference = new StructureRef(UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("minecraft", "the_nether"),
                Identifier.fromNamespaceAndPath("academy", "sable"), 1);
        assertThrows(IllegalArgumentException.class,
                () -> SubLevelTerrainBreakPolicy.requireReference(HOST, reference));
    }
}
