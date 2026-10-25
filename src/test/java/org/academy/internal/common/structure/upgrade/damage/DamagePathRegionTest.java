package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class DamagePathRegionTest {
    @Test void slabEntryHandlesParallelReverseAndZeroLengthPaths() {
        var box = new AABB(0, 0, 0, 1, 1, 1);
        assertEquals(.25, DamagePathRegion.entry(new Vec3(-1,.5,.5), new Vec3(3,.5,.5), box));
        assertEquals(.5, DamagePathRegion.entry(new Vec3(3,.5,.5), new Vec3(-1,.5,.5), box));
        assertEquals(Double.POSITIVE_INFINITY, DamagePathRegion.entry(new Vec3(-1,2,.5), new Vec3(3,2,.5), box));
        assertEquals(0, DamagePathRegion.entry(new Vec3(.5,.5,.5), new Vec3(.5,.5,.5), box));
        assertEquals(Double.POSITIVE_INFINITY, DamagePathRegion.entry(new Vec3(2,2,2), new Vec3(2,2,2), box));
    }

    @Test void rotatedThinSlabRetainsAnEmptyUpperHalf() {
        var pose = new StructurePose(new Vec3(-2160,150,-1480),new Quaterniond().rotateXYZ(.3,.7,.2));
        var start=pose.worldToLocalPoint(pose.localToWorldPoint(new Vec3(-2,.9,.5)));
        var end=pose.worldToLocalPoint(pose.localToWorldPoint(new Vec3(3,.9,.5)));
        assertEquals(Double.POSITIVE_INFINITY,DamagePathRegion.entry(start,end,new AABB(0,0,0,1,.5,1).inflate(.1)));
    }

    @Test void sparseCursorMatchesExhaustiveSelectionInBothDirections() {
        var envelope=new AABB(-5,-4,-3,6,5,4);
        for (var start : new Vec3[]{new Vec3(-8,-3.25,2.3),new Vec3(8,3.25,-2.3)}) {
            var end=start.scale(-1);
            var expected=new HashSet<BlockPos>();
            for (var pos : BlockPos.betweenClosed(-5,-4,-3,5,4,3)) {
                if(Double.isFinite(DamagePathRegion.entry(start,end,new AABB(pos).inflate(.35)))) expected.add(pos.immutable());
            }
            var actual=new HashSet<BlockPos>();
            var cursor=new DamagePathRegion.Cursor(start,end,.35,envelope);
            double previous=-1;
            int work=0;
            while(cursor.hasNext()) {
                assertTrue(++work<5000);
                assertTrue(cursor.frontier()>=previous-1.0e-12);
                previous=cursor.frontier();
                var cell=cursor.step();
                if(cell!=null) assertTrue(actual.add(cell.position()),"duplicate cell");
            }
            assertEquals(expected,actual);
        }
    }

    @Test void longDiagonalDoesNotScanItsBoundingCube() {
        var cursor=new DamagePathRegion.Cursor(new Vec3(.2,.3,.4),new Vec3(500.2,500.3,500.4),.35,
                new AABB(-1,-1,-1,502,502,502));
        int work=0,cells=0;
        while(cursor.hasNext()) { assertTrue(++work<32768); if(cursor.step()!=null) cells++; }
        assertTrue(cells>500 && cells<8000);
    }

    @Test void emptyEnvelopeDoesNotInventAnIntersection() {
        assertFalse(new DamagePathRegion.Cursor(Vec3.ZERO,new Vec3(2,0,0),.1,new AABB(100,0,0,102,2,2)).hasNext());
    }
}
