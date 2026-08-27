package org.academy.api.client.render.vfxgraph.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class GraphCameraTest {
    private static final float FOV = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0f / 9.0f;

    @Test
    void extendsConventionalFarPlane() {
        var camera = cameraWithProjection(new Matrix4f().setPerspective(FOV, ASPECT, 0.05f, 32.0f));

        var extended = camera.withMinimumFarPlane(512.0f);

        assertEquals(0.05f, extended.projection().perspectiveNear(), 1.0E-3f);
        assertEquals(512.0f, extended.projection().perspectiveFar(), 0.5f);
    }

    @Test
    void extendsReversedZFarPlaneWithoutChangingNearPlane() {
        var camera = cameraWithProjection(new Matrix4f().setPerspective(FOV, ASPECT, 32.0f, 0.05f));

        var extended = camera.withMinimumFarPlane(512.0f);

        assertEquals(512.0f, extended.projection().perspectiveNear(), 0.5f);
        assertEquals(0.05f, extended.projection().perspectiveFar(), 1.0E-3f);
    }

    private static GraphCamera cameraWithProjection(Matrix4f projection) {
        return new GraphCamera(new Vector3f(), new Matrix4f(), projection);
    }
}
