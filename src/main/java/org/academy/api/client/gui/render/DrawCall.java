package org.academy.api.client.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DrawCall(
        RenderPipeline pipeline,
        @Nullable ScissorRect scissorArea,
        List<TextureBinding> textures,
        List<UniformBinding> uniforms,
        int baseVertex,
        int indexCount
) {
}