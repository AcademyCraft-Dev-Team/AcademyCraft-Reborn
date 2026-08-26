package org.academy.api.client.render.vfxgraph.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphCameraTest {
    @Test
    void perspectiveCameraProducesFiniteProjection() {
        var camera = GraphCamera.perspective(new Vector3f(0f, 0f, 10f), (float) Math.toRadians(60.0), 16f / 9f, 0.1f, 1000f);

        assertEquals(0f, camera.position().x);
        assertTrue(camera.projection().isFinite());
        // 透视投影 m00 = 1/(aspect*tan(fov/2)) ≈ 0.97
        assertEquals(0.974f, camera.projection().m00(), 0.01f);
    }

    @Test
    void viewRotationIsIdentityByDefault() {
        var camera = GraphCamera.perspective(new Vector3f(1f, 2f, 3f), 1f, 1f, 0.1f, 100f);
        var view = camera.viewRotation();
        assertEquals(1f, view.m00());
        assertEquals(1f, view.m11());
        assertEquals(1f, view.m22());
    }

    @Test
    void fromGameCameraPreservesPositionRotationProjection() {
        var position = new Vector3f(10f, -5f, 30f);
        var view = new Matrix4f().rotationY(0.5f);
        var projection = new Matrix4f().setPerspective(1f, 16f / 9f, 0.1f, 1000f);
        var camera = GraphCamera.fromGameCamera(position, view, projection);

        assertEquals(position, camera.position());
        assertEquals((float) Math.cos(0.5), camera.viewRotation().m00(), 1e-5f);
        assertEquals(projection.m00(), camera.projection().m00(), 1e-5f);
        // 拷贝语义：修改原矩阵不影响相机
        view.m00(99f);
        assertEquals((float) Math.cos(0.5), camera.viewRotation().m00(), 1e-5f);
    }
}
