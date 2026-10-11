package org.academy.api.client.renderer;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetSelectionModelTest {
    @ParameterizedTest
    @CsvSource({"0.2,0.4,0.2", "0.6,1.8,0.6", "0.6,1.5,0.6", "2,0.8,2", "2,1,0.4", "4,8,4"})
    void frameEnclosesCollisionCornersWithoutClosingEdges(double width, double height, double depth) {
        var target = new AABB(12, 64, -9, 12 + width, 64 + height, -9 + depth);
        for (double time : new double[]{0, 0.35, 1, 1.9}) {
            var layout = TargetSelectionModel.layout(target, false, time);
            assertNotNull(layout);
            var center = target.getCenter();
            for (var x : new double[]{target.minX, target.maxX}) {
                for (var z : new double[]{target.minZ, target.maxZ}) {
                    assertTrue(Math.abs(x - center.x) / (layout.bounds().getXsize() / 2)
                            + Math.abs(z - center.z) / (layout.bounds().getZsize() / 2) < 1);
                }
            }
            assertTrue(layout.bounds().minY < target.minY && layout.bounds().maxY > target.maxY);
            assertTrue(layout.cornerLength() * 2 < layout.footprint().get(0).distanceTo(layout.footprint().get(1)));
            assertTrue(layout.verticalLength() * 2 < layout.bounds().getYsize());
        }
    }

    @Test
    void widthDepthAndPoseHeightUpdateIndependently() {
        var layout = TargetSelectionModel.layout(new AABB(-1, 0, -0.2, 1, 1.5, 0.2), false, 0);
        assertEquals(4.08, layout.bounds().getXsize(), 1e-9);
        assertEquals(0.88, layout.bounds().getZsize(), 1e-9);
        assertEquals(1.66, layout.bounds().getYsize(), 1e-9);
    }

    @Test
    void interpolationRetainsCurrentPoseDimensions() {
        var box = new AABB(9.7, 80, 19.7, 10.3, 81.5, 20.3);
        var result = TargetSelectionModel.interpolatedBounds(box, new Vec3(10, 80, 20), new Vec3(9, 79.5, 19));
        assertEquals(box.getXsize(), result.getXsize(), 1e-9);
        assertEquals(1.5, result.getYsize(), 1e-9);
        assertEquals(new Vec3(9, 80.25, 19), result.getCenter());
    }

    @Test
    void supportedFeetKeepTheLowerRingAboveTheGroundWhileAirborneMarkersKeepTheirMargin() {
        var box = new AABB(-0.3, 81, -0.3, 0.3, 82.8, 0.3);
        var ground = TargetSelectionModel.layout(box, false, 0, true);
        var air = TargetSelectionModel.layout(box, false, 0, false);
        assertEquals(81.02, ground.bounds().minY, 1e-9);
        assertEquals(80.92, air.bounds().minY, 1e-9);
        assertEquals(ground.bounds().maxY, air.bounds().maxY);
        var probe = TargetSelectionModel.supportProbe(box);
        assertEquals(box.minY - 0.08, probe.minY, 1e-9);
        assertEquals(box.minY + 0.001, probe.maxY, 1e-9);
        assertEquals(box.getXsize(), probe.getXsize());
        assertEquals(box.getZsize(), probe.getZsize());
        assertTrue(probe.getYsize() < 0.1);
        assertEquals(box, TargetSelectionModel.layout(box, true, 0, true).bounds());
    }

    @Test
    void allEightCornersHaveThickerHorizontalAndVerticalCoreEdges() {
        var layout = TargetSelectionModel.layout(new AABB(0, 0, 0, 0.6, 1.8, 0.6), false, 0);
        var geometry = surfaces(layout, 0);
        for (int corner = 0; corner < 8; corner++) {
            int start = 32 + corner * 4;
            for (int edge = 0; edge < 2; edge++) {
                var horizontal = geometry.get(start + edge).points();
                assertEquals(0.036, horizontal.get(0).distanceTo(horizontal.get(3)), 1e-9);
                var vertical = geometry.get(start + 2 + edge).points();
                assertEquals(0.036, vertical.get(0).distanceTo(vertical.get(1)), 1e-9);
            }
        }
    }

    @Test
    void regionBoundaryDoesNotMoveDuringAnimation() {
        var region = new AABB(-3, 7, 20, 4, 10, 25);
        for (double time : new double[]{0, 0.35, 1}) {
            var layout = TargetSelectionModel.layout(region, true, time);
            assertEquals(region, layout.bounds());
            for (var point : layout.footprint()) {
                assertTrue(point.x == region.minX || point.x == region.maxX);
                assertTrue(point.z == region.minZ || point.z == region.maxZ);
            }
        }
    }

    @Test
    void onlyFaintScanSurfacesCrossTheOpenMiddle() {
        var layout = TargetSelectionModel.layout(new AABB(0, 0, 0, 0.6, 1.8, 0.6), false, 0);
        var surfaces = surfaces(layout, 0);
        assertEquals(66, surfaces.size());
        for (var surface : surfaces) {
            if (surface.points().stream().anyMatch(p -> p.y > layout.bounds().minY + layout.verticalLength() + 1e-9
                    && p.y < layout.bounds().maxY - layout.verticalLength() - 1e-9)) assertTrue(surface.opacity() <= 0.04f);
            for (var point : surface.points()) assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
        }
        assertEquals(64, surfaces(layout, 0.7).size());
        assertEquals(0.5, TargetSelectionModel.scanProgress(0.35), 1e-9);
        assertEquals(1, TargetSelectionModel.scanProgress(1.99));
        assertEquals(0, TargetSelectionModel.scanProgress(2));
    }

    @Test
    void geometryKeepsPrecisionFarFromOrigin() {
        var camera = new Vec3(29_000_000, 81, -29_000_000);
        var box = new AABB(camera.x + 0.2, 80, camera.z + 5, camera.x + 0.8, 81.8, camera.z + 5.6);
        var layout = TargetSelectionModel.layout(box.move(camera.scale(-1)), false, 0);
        assertNotNull(layout);
        assertEquals(0.5, layout.bounds().getCenter().x, 1e-8);
        assertEquals(1.28, layout.bounds().getXsize(), 1e-8);
    }

    @Test
    void invalidOrEmptyBoundsDoNotEmitMarkers() {
        assertNull(TargetSelectionModel.layout(null, false, 0));
        assertNull(TargetSelectionModel.layout(new AABB(0, 0, 0, 0, 1, 1), false, 0));
        assertNull(TargetSelectionModel.layout(new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1), false, 0));
    }

    private record Surface(List<Vec3> points, float opacity) { }
    private static List<Surface> surfaces(TargetSelectionModel.Layout layout, double time) {
        var result = new ArrayList<Surface>();
        TargetSelectionModel.emit(layout, time, 0, (a, b, c, d, opacity) -> result.add(new Surface(List.of(a, b, c, d), opacity)));
        return result;
    }
}
