package org.academy.api.common.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewTargetScannerTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);

    @Test
    void widenedRayBuildsAabbAroundTheDirectedScanPath() {
        var shape = ViewTargetScanner.widenedRay(0.85, 1.15, 0.2, 0.2);

        assertEquals(
                new AABB(-0.85, -1.15, -0.85, 0.85, 1.15, 8.85),
                shape.searchBounds(ORIGIN, FORWARD, 8.0)
        );
    }

    @Test
    void widenedRayMatchesInflatedTargetBoundsWithinItsHitRadius() {
        var shape = ViewTargetScanner.widenedRay(0.85, 1.15, 0.2, 0.2);
        var centered = new AABB(-0.3, -0.9, 3.7, 0.3, 0.9, 4.3);
        var edge = centered.move(0.65, 0.0, 0.0);

        assertEquals(4.0, shape.matchDistance(ORIGIN, FORWARD, 8.0, centered), 1.0e-9);
        assertEquals(4.0, shape.matchDistance(ORIGIN, FORWARD, 8.0, edge), 1.0e-9);
    }

    @Test
    void widenedRayRejectsTargetsOutsideItsPreciseRegionOrRange() {
        var shape = ViewTargetScanner.widenedRay(1.0, 1.0, 0.2, 0.2);
        var besideRay = new AABB(0.71, -0.5, 3.5, 1.31, 0.5, 4.5);
        var behindOrigin = new AABB(-0.3, -0.5, -1.3, 0.3, 0.5, -0.7);
        var beyondRange = new AABB(-0.3, -0.5, 8.7, 0.3, 0.5, 9.3);

        assertTrue(Double.isInfinite(
                shape.matchDistance(ORIGIN, FORWARD, 8.0, besideRay)));
        assertTrue(Double.isInfinite(
                shape.matchDistance(ORIGIN, FORWARD, 8.0, behindOrigin)));
        assertTrue(Double.isInfinite(
                shape.matchDistance(ORIGIN, FORWARD, 8.0, beyondRange)));
    }

    @Test
    void coneUsesTargetCentersAndItsConfiguredAngularThreshold() {
        var cone = ViewTargetScanner.cone(8.0, Math.cos(Math.toRadians(30.0)));
        var inside = centeredAt(new Vec3(2.0, 0.0, 4.0));
        var outsideAngle = centeredAt(new Vec3(3.0, 0.0, 4.0));
        var outsideRange = centeredAt(new Vec3(0.0, 0.0, 9.0));

        assertTrue(Double.isFinite(cone.matchDistance(ORIGIN, FORWARD, 8.0, inside)));
        assertTrue(Double.isInfinite(cone.matchDistance(
                ORIGIN, FORWARD, 8.0, outsideAngle)));
        assertTrue(Double.isInfinite(cone.matchDistance(
                ORIGIN, FORWARD, 8.0, outsideRange)));
    }

    @Test
    void horizontalConeIgnoresVerticalDisplacementForItsAngleOnly() {
        var cone = ViewTargetScanner.horizontalCone(8.0, Math.cos(Math.toRadians(20.0)));
        var highTarget = centeredAt(new Vec3(0.0, 5.0, 4.0));

        assertTrue(Double.isFinite(cone.matchDistance(ORIGIN, FORWARD, 8.0, highTarget)));
    }

    @Test
    void centeredCylinderCanIncludeOrExcludeItsEndCaps() {
        var closed = ViewTargetScanner.centeredCylinder(1.0);
        var open = ViewTargetScanner.openCenteredCylinder(1.0);
        var startCap = centeredAt(new Vec3(0.5, 0.0, 0.0));
        var middle = centeredAt(new Vec3(0.5, 0.0, 4.0));

        assertTrue(Double.isFinite(closed.matchDistance(ORIGIN, FORWARD, 8.0, startCap)));
        assertTrue(Double.isInfinite(open.matchDistance(ORIGIN, FORWARD, 8.0, startCap)));
        assertTrue(Double.isFinite(open.matchDistance(ORIGIN, FORWARD, 8.0, middle)));
    }

    @Test
    void inflatedAabbSegmentUsesTheTargetCollisionBoxRatherThanOnlyItsCenter() {
        var segment = ViewTargetScanner.inflatedAabbSegment(0.25);
        var crossing = new AABB(0.2, -0.5, 3.0, 1.2, 0.5, 4.0);
        var missing = crossing.move(1.0, 0.0, 0.0);

        assertTrue(Double.isFinite(segment.matchDistance(ORIGIN, FORWARD, 8.0, crossing)));
        assertTrue(Double.isInfinite(segment.matchDistance(ORIGIN, FORWARD, 8.0, missing)));
    }

    @Test
    void inflatedAabbSegmentEndpointPoliciesRemainExplicit() {
        var atOrigin = centeredAt(ORIGIN);
        var atEnd = new AABB(-0.1, -0.1, 8.0, 0.1, 0.1, 8.2);

        assertTrue(Double.isFinite(ViewTargetScanner.inflatedAabbSegment(0.0)
                .matchDistance(ORIGIN, FORWARD, 8.0, atOrigin)));
        assertTrue(Double.isInfinite(ViewTargetScanner.inflatedAabbSegmentExcludingOrigin(0.0)
                .matchDistance(ORIGIN, FORWARD, 8.0, atOrigin)));
        assertTrue(Double.isFinite(ViewTargetScanner.inflatedAabbSegment(0.0)
                .matchDistance(ORIGIN, FORWARD, 8.0, atEnd)));
        assertTrue(Double.isInfinite(ViewTargetScanner.openInflatedAabbSegment(0.0)
                .matchDistance(ORIGIN, FORWARD, 8.0, atEnd)));
    }

    @Test
    void unionAcceptsCandidatesFromAnyChildShape() {
        var union = ViewTargetScanner.union(
                ViewTargetScanner.cone(4.0, 0.9),
                ViewTargetScanner.cone(8.0, 0.99)
        );

        assertTrue(Double.isFinite(union.matchDistance(
                ORIGIN, FORWARD, 8.0, centeredAt(new Vec3(1.0, 0.0, 3.0)))));
        assertTrue(Double.isFinite(union.matchDistance(
                ORIGIN, FORWARD, 8.0, centeredAt(new Vec3(0.0, 0.0, 7.0)))));
    }

    @Test
    void shapeConfigurationRejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> ViewTargetScanner.widenedRay(-0.1, 1.0, 0.2, 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> ViewTargetScanner.widenedRay(1.0, Double.NaN, 0.2, 0.2));
    }

    @Test
    void pointToAabbDistanceIsSharedByViewShapes() {
        var box = new AABB(2.0, 1.0, 3.0, 4.0, 2.0, 5.0);

        assertEquals(0.0, ViewTargetScanner.distanceToBoxSqr(
                new Vec3(3.0, 1.5, 4.0), box), 1.0e-9);
        assertEquals(4.0, ViewTargetScanner.distanceToBoxSqr(
                new Vec3(6.0, 1.5, 4.0), box), 1.0e-9);
    }

    @Test
    void segmentGeometryHelpersHandleProjectionAndAabbEntry() {
        assertEquals(4.0, ViewTargetScanner.distanceToSegmentSqr(
                new Vec3(2.0, 0.0, 4.0), ORIGIN, new Vec3(0.0, 0.0, 8.0)), 1.0e-9);
        assertEquals(0.375, ViewTargetScanner.intersectionProgress(
                ORIGIN,
                new Vec3(0.0, 0.0, 8.0),
                new AABB(-1.0, -1.0, 3.0, 1.0, 1.0, 5.0)
        ).orElseThrow(), 1.0e-9);
    }

    private static AABB centeredAt(Vec3 center) {
        return new AABB(center, center).inflate(0.25);
    }
}
