package org.academy.api.client.gui.util;

import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.GlyphDrawCommand;
import org.academy.api.client.gui.msdf.core.MsdfConstants;
import org.academy.api.client.gui.msdf.layout.MsdfTextProcessor;

import java.util.ArrayList;
import java.util.List;

public final class GlyphCommandGenerator {
    private GlyphCommandGenerator() {
    }

    public static List<DrawCommand> generate(
            String text,
            float fontSize, float thickness,
            float red, float green, float blue, float alpha
    ) {
        List<DrawCommand> commands = new ArrayList<>();
        var instances = MsdfTextProcessor.layout(text, fontSize);

        for (var instance : instances) {

            commands.add(new GlyphDrawCommand(
                    instance.textureView(),
                    instance.x(),
                    instance.y(),
                    instance.quadWidth(),
                    instance.quadHeight(),
                    instance.u0(), instance.v0(), instance.u1(), instance.v1(),
                    red, green, blue, alpha,
                    MsdfConstants.DEFAULT_PX_RANGE,
                    thickness
            ));
        }

        return commands;
    }
}