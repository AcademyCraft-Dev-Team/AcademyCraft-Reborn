package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubLevelGrowthTest {
    @Test void budgetKeepsOversizedOldSavesMineableAndCountsTwoCellReplacement() {
        assertTrue(SubLevelGrowth.blockBudgetAllows(16000,0,true,4096));
        assertTrue(SubLevelGrowth.blockBudgetAllows(256,1,true,256));
        assertFalse(SubLevelGrowth.blockBudgetAllows(256,2,true,256));
        assertFalse(SubLevelGrowth.blockBudgetAllows(256,1,false,256));
        assertTrue(SubLevelGrowth.blockBudgetAllows(254,2,false,256));
    }
    @Test void expansionAllowsBothSignsAndOldChunkSeamsWithoutMovingOrigin() {
        var seed = new AABB(0, 0, 0, 1, 1, 1);
        var expanded = SubLevelGrowth.include(seed, new BlockPos(-33, -2, 34), 1, -64, 320);
        assertEquals(new AABB(-34, -3, 0, 1, 1, 36), expanded);
        assertTrue(SubLevelGrowth.valid(expanded, -64, 320));
    }
    @Test void growthBudgetsRejectPathologicalScansAndHeight() {
        assertFalse(SubLevelGrowth.valid(new AABB(0,0,0,257,1,1), -64,320));
        assertFalse(SubLevelGrowth.valid(new AABB(0,0,0,100,100,100), -64,320));
        assertFalse(SubLevelGrowth.valid(new AABB(-513,0,0,-510,1,1), -64,320));
        assertFalse(SubLevelGrowth.valid(new AABB(0,319,0,1,321,1), -64,320));
        assertFalse(SubLevelGrowth.valid(new AABB(Double.NaN,0,0,1,1,1), -64,320));
        assertTrue(SubLevelGrowth.valid(new AABB(-128,-64,0,128,-63,1), -64,320));
    }
    @Test void virtualPlotsCannotOverlapEachOtherOrLegacyStorage() {
        var layout = new PlotLayout(1,0,0,4096);
        var legacy = new PlotPos(63,63);
        var first = new PlotPos(PlotLayout.SPACIOUS_MARKER,0);
        var second = new PlotPos(PlotLayout.SPACIOUS_MARKER+1,0);
        assertEquals(126, layout.chunkMinX(legacy));
        assertTrue(layout.chunkMinX(first)-32 > layout.chunkMinX(legacy)+1);
        assertEquals(64, layout.chunkMinX(second)-layout.chunkMinX(first));
        assertTrue(layout.containsLocal(first,-512,511));
        assertFalse(layout.containsLocal(first,512,0));
        assertFalse(layout.containsLocal(legacy,-1,0));
    }
}
