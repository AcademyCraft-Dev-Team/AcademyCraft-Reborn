package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureCollisionBoxesTest {
    private static final AABB UNIT = new AABB(0, 0, 0, 1, 1, 1);

    @Test
    void axisAlignmentAcceptsQuarterTurnsAndRejectsFreeRotation() {
        assertTrue(NativeStructureCollisionBoxes.axisAligned(StructurePose.identity(Vec3.ZERO)));
        assertTrue(NativeStructureCollisionBoxes.axisAligned(
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 2))));
        assertTrue(NativeStructureCollisionBoxes.axisAligned(
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationX(Math.PI))));
        assertTrue(NativeStructureCollisionBoxes.axisAligned(
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(Math.PI / 2, Math.PI, -Math.PI / 2))));
        assertFalse(NativeStructureCollisionBoxes.axisAligned(
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 4))));
        assertFalse(NativeStructureCollisionBoxes.axisAligned(
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(0.2, 0.3, 0.1))));
    }

    @Test
    void transformBoxMatchesAnIndependentCornerRotation() {
        var rotation = new Quaterniond().rotationY(Math.PI / 2);
        var pose = new StructurePose(new Vec3(10, 2, -3), rotation);
        var transformed = NativeStructureCollisionBoxes.transformBox(UNIT, pose);
        var min = new Vector3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        var max = new Vector3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            var local = new Vector3d((corner & 1) == 0 ? 0 : 1, (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1);
            rotation.transform(local);
            local.add(10, 2, -3);
            min.min(local);
            max.max(local);
        }
        assertEquals(min.x, transformed.minX, 1.0E-12);
        assertEquals(min.y, transformed.minY, 1.0E-12);
        assertEquals(min.z, transformed.minZ, 1.0E-12);
        assertEquals(max.x, transformed.maxX, 1.0E-12);
        assertEquals(max.y, transformed.maxY, 1.0E-12);
        assertEquals(max.z, transformed.maxZ, 1.0E-12);
    }

    @Test
    void axisAlignedWorldBoxesStayExactAndFreeRotationStaysBoundedAndCovered() {
        var exact = NativeStructureCollisionBoxes.worldBoxes(List.of(UNIT),
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 2)));
        assertEquals(1, exact.size());
        assertEquals(1.0, exact.getFirst().getXsize(), 1.0E-12);
        assertEquals(1.0, exact.getFirst().getZsize(), 1.0E-12);

        var rotated = NativeStructureCollisionBoxes.worldBoxes(List.of(UNIT),
                new StructurePose(new Vec3(0, 0, 0), new Quaterniond().rotationY(Math.PI / 4)));
        assertTrue(rotated.size() > 1, "Free rotation requires subdivision");
        assertTrue(rotated.size() <= NativeStructureCollisionBoxes.MAX_WORLD_BOXES);
        var hull = NativeStructureCollisionBoxes.transformBox(UNIT,
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationY(Math.PI / 4)));
        for (var box : rotated) {
            assertTrue(box.minX >= hull.minX - 1.0E-9 && box.maxX <= hull.maxX + 1.0E-9
                            && box.minY >= hull.minY - 1.0E-9 && box.maxY <= hull.maxY + 1.0E-9
                            && box.minZ >= hull.minZ - 1.0E-9 && box.maxZ <= hull.maxZ + 1.0E-9,
                    "Subdivided boxes must stay inside the true corner hull");
        }
        var center = new AABB(0.49, 0.49, 0.49, 0.51, 0.51, 0.51);
        assertTrue(rotated.stream().anyMatch(box -> box.intersects(center)),
                "The rotated cube center must remain covered");
    }

    @Test
    void subdivisionBudgetShrinksPartsInsteadOfExploding() {
        var boxes = new java.util.ArrayList<AABB>(512);
        for (int index = 0; index < 512; index++) boxes.add(UNIT.move(index, 0, 0));
        var rotated = NativeStructureCollisionBoxes.worldBoxes(boxes,
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(0.2, 0.3, 0.1)));
        assertTrue(rotated.size() <= NativeStructureCollisionBoxes.MAX_WORLD_BOXES);
        assertFalse(rotated.isEmpty());
    }

    @Test
    void supportSurfaceMatchesTheLegacyToleranceSemantics() {
        var platform = List.of(new AABB(0, 0, 0, 4, 1, 4), new AABB(2, 0, 0, 6, 2, 4));
        var standing = new AABB(1, 1.0, 1, 1.5, 2.8, 1.5);
        assertEquals(1.0, NativeStructureCollisionBoxes.supportSurfaceY(platform, standing, 0.18).orElseThrow(),
                1.0E-12);
        var aboveHigher = new AABB(4, 2.0, 1, 4.5, 3.8, 1.5);
        assertEquals(2.0, NativeStructureCollisionBoxes.supportSurfaceY(platform, aboveHigher, 0.18).orElseThrow(),
                1.0E-12);
        assertTrue(NativeStructureCollisionBoxes.supports(platform, standing, 0.18));
        assertTrue(NativeStructureCollisionBoxes.supportSurfaceY(platform,
                new AABB(1, 1.4, 1, 1.5, 3.2, 1.5), 0.18).isEmpty(),
                "A clearly falling entity is not supported");
        assertTrue(NativeStructureCollisionBoxes.supportSurfaceY(platform,
                new AABB(10, 1.0, 10, 10.5, 2.8, 10.5), 0.18).isEmpty(),
                "Horizontal separation must not count as support");
        assertTrue(NativeStructureCollisionBoxes.supports(platform,
                new AABB(1, 1.3, 1, 1.5, 3.1, 1.5), 0.5),
                "The recovery tolerance accepts a small gap");
    }
}
