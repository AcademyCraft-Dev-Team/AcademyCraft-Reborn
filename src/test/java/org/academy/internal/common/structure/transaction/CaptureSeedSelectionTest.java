package org.academy.internal.common.structure.transaction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureSeedSelectionTest {
    @Test void choosesSupportAfterDecorationWithoutDroppingOrReorderingTheSelection() {
        var source = List.of("grass", "wheat", "farmland", "stone");
        var checked = new ArrayList<Integer>();
        int seed = StructureCaptureCoordinator.firstSeedIndex(source.size(), index -> {
            checked.add(index);
            return source.get(index).equals("farmland") || source.get(index).equals("stone");
        });
        assertEquals(2, seed);
        assertEquals(List.of(0, 1, 2), checked);
        assertEquals(List.of("grass", "wheat", "farmland", "stone"), source);
    }

    @Test void noSustainableSeedRefusesInsteadOfFallingBackToTheFirstDecorativeCell() {
        assertEquals(-1, StructureCaptureCoordinator.firstSeedIndex(3, index -> false));
        assertEquals(-1, StructureCaptureCoordinator.firstSeedIndex(0, index -> true));
        assertEquals(0, StructureCaptureCoordinator.firstSeedIndex(3, index -> true));
    }
}
