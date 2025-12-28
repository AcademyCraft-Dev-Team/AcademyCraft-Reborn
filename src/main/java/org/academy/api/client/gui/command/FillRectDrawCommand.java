package org.academy.api.client.gui.command;

import org.academy.api.client.Render;

import java.util.List;

public class FillRectDrawCommand extends PosColorRectDrawCommand {
    public FillRectDrawCommand(
            float width, float height,
            float red, float green, float blue, float alpha
    ) {
        super(Render.RenderPipelines.POS_COLOR, width, height, red, green, blue, alpha, List.of(), List.of());
    }
}