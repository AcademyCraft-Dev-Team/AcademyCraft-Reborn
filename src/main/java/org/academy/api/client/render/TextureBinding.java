package org.academy.api.client.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

public record TextureBinding(String name, GpuTextureView view, GpuSampler sampler) {
}