package org.academy.internal.client.ability.mentalout;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RtsOrbitGeometryTest {
    @Test void pivotProjectsToViewportCenterThroughRotationAndZoom() {
        var pivot = new Vec3(42, 80, -27);
        float fov = (float) Math.toRadians(60), aspect = 16f / 9;
        double ndcX = .32, ndcY = .18;
        var tangent = Math.tan(fov / 2);
        for (float yaw : new float[]{-170, 0, 45, 170}) for (float pitch : new float[]{20, 62, 85}) {
            for (double distance : new double[]{2, 24, 1024}) {
                var eye = RtsOrbitGeometry.cameraPosition(pivot, distance, pitch, yaw, ndcX * tangent * aspect, ndcY * tangent);
                var look = eye.add(Vec3.directionFromRotation(pitch, yaw));
                var view = new Matrix4f().lookAt((float) eye.x, (float) eye.y, (float) eye.z,
                        (float) look.x, (float) look.y, (float) look.z, 0, 1, 0);
                var projection = new Matrix4f().perspective(fov, aspect, .05f, 20000);
                var screen = new Vector4f((float) pivot.x, (float) pivot.y, (float) pivot.z, 1).mul(view).mul(projection);
                assertEquals(ndcX, screen.x / screen.w, .002, "Orbit moved pivot away from viewport center");
                assertEquals(ndcY, screen.y / screen.w, .002, "Zoom moved pivot vertically");
                assertEquals(distance, eye.distanceTo(pivot), .0001);
            }
        }
    }
}
