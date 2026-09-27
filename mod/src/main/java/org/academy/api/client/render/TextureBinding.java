package org.academy.api.client.render;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public record TextureBinding(String name, GpuTextureView view, GpuSampler sampler) {
}
