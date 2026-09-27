package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureSpatialIndexTest {
    @Test
    void findsLargeStructureAtFarCornerOutsideOriginSection() {
        var index = new BlockStructureSpatialIndex<Object>();
        var structure = new Object();
        index.update(structure, new AABB(0.0, 64.0, 0.0, 31.0, 80.0, 31.0));

        var result = index.query(new AABB(29.0, 79.9, 29.0, 30.0, 80.2, 30.0));

        assertEquals(1, result.size());
        assertTrue(result.getFirst() == structure);
    }

    @Test
    void movingStructureRemovesOldSectionCoverage() {
        var index = new BlockStructureSpatialIndex<Object>();
        var structure = new Object();
        index.update(structure, new AABB(0.0, 64.0, 0.0, 31.0, 80.0, 31.0));
        index.update(structure, new AABB(64.0, 64.0, 64.0, 95.0, 80.0, 95.0));

        assertTrue(index.query(new AABB(29.0, 79.9, 29.0, 30.0, 80.2, 30.0)).isEmpty());
        assertEquals(1, index.query(
                new AABB(93.0, 79.9, 93.0, 94.0, 80.2, 94.0)).size());
    }

    @Test
    void returnsOneCandidateWhenQuerySpansSeveralCoveredSections() {
        var index = new BlockStructureSpatialIndex<Object>();
        var structure = new Object();
        index.update(structure, new AABB(0.0, 64.0, 0.0, 31.0, 80.0, 31.0));

        assertEquals(1, index.query(
                new AABB(8.0, 70.0, 8.0, 24.0, 80.1, 24.0)).size());
    }
}
