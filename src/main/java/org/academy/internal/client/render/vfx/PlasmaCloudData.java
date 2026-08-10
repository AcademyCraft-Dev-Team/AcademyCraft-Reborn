package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;

import java.nio.ByteBuffer;

public record PlasmaCloudData(ByteBuffer instances) implements VfxRenderData {
}
