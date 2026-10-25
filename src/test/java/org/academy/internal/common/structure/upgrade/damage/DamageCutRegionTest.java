package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureCutDamage;
import org.academy.api.common.structure.upgrade.StructureDamage;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageCutRegionTest {
    private static StructureCutDamage.Request request(Vec3 start, Vec3 end, Vec3 right, double width, double thickness) {
        return new StructureCutDamage.Request(start, end, right, width, thickness,
                (state, view, pos) -> true, StructureDamage.DropPolicy.NO_DROPS, false);
    }

    @Test void bladeWidthDoesNotInflateItsThinAxis() {
        var region = new DamageCutRegion(request(Vec3.ZERO, new Vec3(10,0,0), new Vec3(0,0,1),4,.55));
        var pose = StructurePose.identity(Vec3.ZERO);
        assertTrue(region.intersects(new AABB(3,-.2,3.8,4,.2,4.2),pose));
        assertFalse(region.intersects(new AABB(3,.6,-.2,4,1,.2),pose));
        assertFalse(region.intersects(new AABB(11,-.2,-.2,12,.2,.2),pose));
    }

    @Test void verticalPlaneIncludesFullWidthAndExcludesOldHorizontalPlane() {
        var region = new DamageCutRegion(request(Vec3.ZERO,new Vec3(10,0,0),new Vec3(0,1,0),4,.55));
        var pose = StructurePose.identity(Vec3.ZERO);
        assertTrue(region.intersects(new AABB(3,3.5,-.2,4,3.9,.2),pose));
        assertFalse(region.intersects(new AABB(3,-.2,2,4,.2,3),pose));
    }

    @Test void commonRigidTransformPreservesHitAndSlabGap() {
        var pose = new StructurePose(new Vec3(-2500,210,4000),new Quaterniond().rotateXYZ(.31,.72,-.48));
        var start=pose.localToWorldPoint(new Vec3(-2,.8,.5));
        var end=pose.localToWorldPoint(new Vec3(3,.8,.5));
        var region = new DamageCutRegion(request(start,end,pose.localToWorldDirection(new Vec3(0,0,1)),.2,.1));
        assertFalse(region.intersects(new AABB(0,0,0,1,.5,1),pose));
        assertTrue(region.intersects(new AABB(0,0,0,1,1,1),pose));
    }

    @Test void enclosingBoundsDoNotHitRotatedEmptyCorners() {
        var pose = new StructurePose(Vec3.ZERO,new Quaterniond().rotateZ(Math.PI/4));
        var region = new DamageCutRegion(request(new Vec3(1.35,-.1,0),new Vec3(1.35,.1,0),new Vec3(0,0,1),.1,.02));
        assertFalse(region.intersects(new AABB(-1,-.05,-.1,1,.05,.1),pose));
    }

    @Test void rejectsDegenerateOrUnboundedVolumes() {
        assertThrows(IllegalArgumentException.class,()->request(Vec3.ZERO,Vec3.ZERO,new Vec3(0,1,0),4,.55));
        assertThrows(IllegalArgumentException.class,()->request(Vec3.ZERO,new Vec3(1,0,0),new Vec3(1,0,0),4,.55));
        assertThrows(IllegalArgumentException.class,()->request(Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,1,0),32,1));
        assertThrows(IllegalArgumentException.class,()->request(Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,1,0),4,0));
        assertThrows(IllegalArgumentException.class,()->request(Vec3.ZERO,new Vec3(Double.NaN,0,0),new Vec3(0,1,0),4,.55));
    }
}
