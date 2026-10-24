package org.academy.internal.common.sublevel;

import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainSectionGeometryTest {
    private static PhysicsBox cube(double x, double y, double z, double size) {
        return new PhysicsBox(new PhysicsVector(x + size / 2, y + size / 2, z + size / 2),
                PhysicsQuaternion.IDENTITY, new PhysicsVector(size / 2, size / 2, size / 2));
    }

    private static PhysicsBox box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new PhysicsBox(new PhysicsVector((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2),
                PhysicsQuaternion.IDENTITY, new PhysicsVector((maxX - minX) / 2, (maxY - minY) / 2, (maxZ - minZ) / 2));
    }

    private static double volume(List<PhysicsBox> boxes) {
        return boxes.stream().mapToDouble(box -> 8.0 * box.halfExtents().x() * box.halfExtents().y()
                * box.halfExtents().z()).sum();
    }

    @Test
    void solidCubePacksIntoOneBox() {
        var boxes = new ArrayList<PhysicsBox>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            boxes.add(cube(x, y, z, 1));
        }
        var merged = TerrainSectionGeometry.merge(boxes);
        assertEquals(1, merged.size());
        assertEquals(1.0, merged.getFirst().halfExtents().x(), 1.0E-9);
        assertEquals(1.0, merged.getFirst().center().x(), 1.0E-9);
        assertEquals(8.0, volume(merged), 1.0E-9);
    }

    @Test
    void denseSectionPacksFully() {
        var boxes = new ArrayList<PhysicsBox>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            boxes.add(cube(x, y, z, 1));
        }
        var merged = TerrainSectionGeometry.merge(boxes);
        assertEquals(1, merged.size(), "a fully solid section must become one exact box");
        assertEquals(4096.0, volume(merged), 1.0E-6);
    }

    @Test
    void denseMergeCompletesWithinTheLinearBudget() {
        var boxes = new ArrayList<PhysicsBox>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            boxes.add(cube(x, y, z, 1));
        }
        assertTimeout(java.time.Duration.ofSeconds(5), () -> TerrainSectionGeometry.merge(boxes),
                "4096 cells must not require the former pairwise merge");
    }

    @Test
    void separatedBoxesAreNotJoined() {
        var merged = TerrainSectionGeometry.merge(List.of(cube(0, 0, 0, 1), cube(2, 0, 0, 1)));
        assertEquals(2, merged.size());
    }

    @Test
    void fractionalStackMergesAlongY() {
        var merged = TerrainSectionGeometry.merge(List.of(
                box(0, 0, 0, 1, 0.5, 1), box(0, 0.5, 0, 1, 1, 1)));
        assertEquals(1, merged.size());
        assertEquals(0.5, merged.getFirst().halfExtents().y(), 1.0E-9);
    }

    @Test
    void adjacentHalvesMergeAlongX() {
        var merged = TerrainSectionGeometry.merge(List.of(
                box(0, 0, 0, 0.5, 1, 1), box(0.5, 0, 0, 1, 1, 1)));
        assertEquals(1, merged.size());
        assertEquals(0.5, merged.getFirst().halfExtents().x(), 1.0E-9);
    }

    @Test
    void missingCornerStaysEmpty() {
        var boxes = new ArrayList<PhysicsBox>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            if (x == 1 && y == 1 && z == 1) continue;
            boxes.add(cube(x, y, z, 1));
        }
        var merged = TerrainSectionGeometry.merge(boxes);
        assertEquals(7.0, volume(merged), 1.0E-9);
        for (var box : merged) {
            var center = box.center();
            var half = box.halfExtents();
            double minX = center.x() - half.x(), maxX = center.x() + half.x();
            double minY = center.y() - half.y(), maxY = center.y() + half.y();
            double minZ = center.z() - half.z(), maxZ = center.z() + half.z();
            assertFalse(minX <= 1.5 && maxX >= 1.5 && minY <= 1.5 && maxY >= 1.5
                    && minZ <= 1.5 && maxZ >= 1.5, "the missing corner must remain empty");
        }
    }

    @Test
    void mergeIsIdempotent() {
        var boxes = new ArrayList<PhysicsBox>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            if (x == 1 && z == 1) continue;
            boxes.add(cube(x, 0, z, 1));
        }
        var once = TerrainSectionGeometry.merge(boxes);
        var twice = TerrainSectionGeometry.merge(once);
        assertEquals(once.size(), twice.size());
        assertEquals(volume(once), volume(twice), 1.0E-9);
    }

    @Test
    void singleBoxIsReturnedUnchanged() {
        var input = List.of(cube(3, 4, 5, 0.5));
        assertEquals(input, TerrainSectionGeometry.merge(input));
    }
}
