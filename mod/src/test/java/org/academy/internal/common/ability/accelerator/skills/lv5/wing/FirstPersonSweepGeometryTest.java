
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.academy.internal.common.ability.accelerator.Accelerator;

class FirstPersonSweepGeometryTest {
    @Test
    void wingProjectionsAreMirroredAndStayInFrontOfCamera() {
        for (var i = 0; i <= 20; i++) {
            var progress = i / 20.0f;
            var left = Accelerator.Client.FirstPersonSweepGeometry.wingProjection(true, progress);
            var right = Accelerator.Client.FirstPersonSweepGeometry.wingProjection(false, progress);

            assertEquals(-right.rootX(), left.rootX(), 0.0001f);
            assertEquals(right.rootY(), left.rootY(), 0.0001f);
            assertEquals(right.rootZ(), left.rootZ(), 0.0001f);
            assertEquals(-right.sweepDegrees(), left.sweepDegrees(), 0.0001f);

            for (var j = 0; j <= 10; j++) {
                var leftCenter = left.centerline(j / 10.0f);
                var rightCenter = right.centerline(j / 10.0f);
                assertEquals(-rightCenter.x(), leftCenter.x(), 0.0001f);
                assertEquals(rightCenter.y(), leftCenter.y(), 0.0001f);
                assertEquals(rightCenter.z(), leftCenter.z(), 0.0001f);
                assertTrue(leftCenter.z() <= -0.78f);
            }
        }
    }

    @Test
    void wingProjectionFadesAtLifetimeBoundaries() {
        assertEquals(0.0f, Accelerator.Client.FirstPersonSweepGeometry.wingProjection(true, 0.0f).alpha(), 0.0001f);
        assertEquals(0.0f, Accelerator.Client.FirstPersonSweepGeometry.wingProjection(true, 1.0f).alpha(), 0.0001f);
        assertTrue(Accelerator.Client.FirstPersonSweepGeometry.wingProjection(true, 0.5f).alpha() > 0.0f);
    }

}
