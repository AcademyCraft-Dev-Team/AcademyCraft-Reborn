package org.academy.api.common.vfx;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.ViewTargetScanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectionalAreaTest {
    private final DirectionalArea area = new DirectionalArea(new Vec3(9, 72, -13), new Vec3(0, 0.5, 1),
            new DirectionalArea.Cone(16, Math.cos(Math.toRadians(25))),
            new DirectionalArea.Cone(12, Math.cos(Math.toRadians(52))));

    @Test void surfaceMembershipMatchesTheCombatCenterTestIncludingPitchAndBothBranches() {
        for (int x = -17; x <= 17; x++) for (int y = -17; y <= 17; y++) for (int z = -17; z <= 17; z++) {
            var point = area.origin().add(x, y, z);
            var box = new AABB(point.add(-0.4, -0.9, -0.4), point.add(0.4, 0.9, 0.4));
            assertEquals(ViewTargetScanner.matches(area.origin(), area.direction(), area.radius(), area.shape(), box),
                    area.contains(point), () -> "Visual/combat disagreement at " + point);
        }
    }

    @Test void wideNearRegionAndNarrowFarRegionAreNotReplacedByOneCone() {
        var straight = new DirectionalArea(Vec3.ZERO, new Vec3(0, 0, 1), area.first(), area.second());
        assertTrue(straight.contains(new Vec3(7, 0, 8)));
        assertFalse(straight.contains(new Vec3(9, 0, 12)));
        assertTrue(straight.contains(new Vec3(0, 0, 15)));
        assertFalse(straight.contains(new Vec3(0, 0, 16.01)));
        assertFalse(straight.contains(new Vec3(0, 0, -1)));
    }

    @Test void cameraAirAttenuationIsContinuousAndDoesNotDependOnCameraMode() {
        var straight = new DirectionalArea(Vec3.ZERO, new Vec3(0, 0, 1), area.first(), area.second());
        assertEquals(0.2f, straight.airVisibility(new Vec3(0, 0, 5)), 0.0001);
        assertEquals(1, straight.airVisibility(new Vec3(20, 0, 5)), 0.0001);
        assertTrue(Math.abs(straight.airVisibility(new Vec3(0, 0, 15.99))
                - straight.airVisibility(new Vec3(0, 0, 16.01))) < 0.05);
    }

    @Test void invalidDirectionsAndSectorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DirectionalArea(Vec3.ZERO, Vec3.ZERO, area.first(), area.second()));
        assertThrows(IllegalArgumentException.class, () -> new DirectionalArea.Cone(Double.NaN, 0.8));
        assertThrows(IllegalArgumentException.class, () -> new DirectionalArea.Cone(10, 2));
    }

    @Test void scaledRangesAndApexUseTheSameSelectionRule() {
        for (double radius : new double[]{0, 12, 24, 2048}) {
            var cone = new DirectionalArea.Cone(radius, 0.9);
            var field = new DirectionalArea(Vec3.ZERO, new Vec3(0, 1, 1), cone, cone);
            for (var point : new Vec3[]{Vec3.ZERO, new Vec3(0, 0, -0.00001),
                    field.direction().scale(radius), field.direction().scale(radius + 0.01)}) {
                assertEquals(ViewTargetScanner.matches(Vec3.ZERO, field.direction(), radius,
                        field.shape(), new AABB(point, point)), field.contains(point));
            }
        }
    }
}
