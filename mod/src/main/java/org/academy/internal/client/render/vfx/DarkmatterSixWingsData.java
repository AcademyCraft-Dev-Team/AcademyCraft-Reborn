package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

public record DarkmatterSixWingsData(Matrix4f root, ByteBuffer vertices, int vertexCount) implements VfxRenderData {
}
