package org.academy.api.client.render;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

public record UniformBinding(String name, GpuBufferSlice slice) {
}
