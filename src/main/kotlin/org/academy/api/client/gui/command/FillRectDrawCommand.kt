package org.academy.api.client.gui.command

import org.academy.api.client.Render

class FillRectDrawCommand(
    width: Float, height: Float,
    red: Float, green: Float, blue: Float, alpha: Float
) : PosColorRectDrawCommand(
    Render.RenderPipelines.POS_COLOR,
    width,
    height,
    red,
    green,
    blue,
    alpha,
    listOf(),
    listOf()
)