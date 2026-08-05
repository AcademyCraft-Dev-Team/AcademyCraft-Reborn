package org.academy.api.client.render.vfx;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record VfxCamera(
        Vector3f pos,
        Quaternionf orientation,
        Matrix4f projectionMatrix,
        Matrix4f viewRotationMatrix,
        float fov
) {
}
