package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SubLevelConnectedComponentsTest {
    @Test void sixFacesConnectButEdgesAndCornersDoNotAndResultsAreDeterministic() {
        var cells = new ArrayList<>(List.of(new BlockPos(1,0,0), new BlockPos(0,0,0),
                new BlockPos(1,1,0), new BlockPos(2,2,0), new BlockPos(3,3,1)));
        var original = List.copyOf(cells);
        var result = SubLevelConnectedComponents.find(cells);
        assertEquals(List.of(List.of(new BlockPos(0,0,0), new BlockPos(1,0,0), new BlockPos(1,1,0)),
                List.of(new BlockPos(2,2,0)), List.of(new BlockPos(3,3,1))), result);
        assertEquals(original, cells);
        Collections.reverse(cells);
        assertEquals(result, SubLevelConnectedComponents.find(cells));
        assertEquals(new HashSet<>(original), new HashSet<>(result.stream().flatMap(List::stream).toList()));
        assertThrows(UnsupportedOperationException.class, () -> result.add(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> result.getFirst().add(BlockPos.ZERO));
    }
    @Test void boundariesAndComponentBudgetsPreserveTheOriginalInsteadOfReturningPartialSplits() {
        assertTrue(SubLevelConnectedComponents.find(List.of()).isEmpty());
        assertEquals(1, SubLevelConnectedComponents.find(IntStream.range(0,4096).mapToObj(x -> new BlockPos(x,0,0)).toList()).size());
        assertTrue(SubLevelConnectedComponents.find(IntStream.range(0,4097).mapToObj(x -> new BlockPos(x,0,0)).toList()).isEmpty());
        assertEquals(64, SubLevelConnectedComponents.find(IntStream.range(0,64).mapToObj(x -> new BlockPos(x*2,0,0)).toList()).size());
        assertTrue(SubLevelConnectedComponents.find(IntStream.range(0,65).mapToObj(x -> new BlockPos(x*2,0,0)).toList()).isEmpty());
        assertEquals(2, SubLevelConnectedComponents.find(List.of(new BlockPos(Integer.MIN_VALUE,0,0), new BlockPos(Integer.MAX_VALUE,0,0))).size());
    }
    @Test void mutableInputsAreCopiedAndDuplicatesCannotDuplicateContent() {
        var position = new BlockPos.MutableBlockPos(1,2,3);
        var result = SubLevelConnectedComponents.find(List.of(position, position));
        position.set(9,9,9);
        assertEquals(List.of(List.of(new BlockPos(1,2,3))), result);
    }
}
