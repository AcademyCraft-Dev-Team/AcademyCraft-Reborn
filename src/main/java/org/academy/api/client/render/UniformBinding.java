package org.academy.api.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

public record UniformBinding(String name, GpuBufferSlice slice) {
}