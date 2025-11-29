package org.academy.api.client.gui.command;

import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;

import java.util.List;

public class FillRectDrawCommand extends PosColorRectDrawCommand {
    public FillRectDrawCommand(
            float width,
            float height,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        super(Render.RenderPipelines.POS_COLOR, width, height, red, green, blue, alpha);
    }

    @Override
    public List<TextureBinding> getTextures() {
        return List.of();
    }

    @Override
    public List<UniformBinding> getUniforms() {
        return List.of();
    }
}