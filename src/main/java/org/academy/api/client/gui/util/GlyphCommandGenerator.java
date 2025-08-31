package org.academy.api.client.gui.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.command.GlyphDrawCommand;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class GlyphCommandGenerator {
    private GlyphCommandGenerator() {
    }

    public static List<DrawCommand> generate(
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow
    ) {
        var visitor = new CommandGlyphVisitor();
        var preparedText = font.prepareText(text, x, y, color, shadow, 0);
        preparedText.visit(visitor);
        return visitor.getCommands();
    }

    public static List<DrawCommand> generate(
            Font font,
            Component text,
            float x,
            float y,
            int color,
            boolean shadow
    ) {
        var orderedText = text.getVisualOrderText();
        return generate(font, orderedText, x, y, color, shadow);
    }

    public static List<DrawCommand> generate(
            Font font,
            String text,
            float x,
            float y,
            int color,
            boolean shadow
    ) {
        return generate(font, Component.literal(text), x, y, color, shadow);
    }

    private static class CommandGlyphVisitor implements Font.GlyphVisitor {
        private final List<DrawCommand> commandList = new ArrayList<>();

        @Override
        public void acceptGlyph(BakedGlyph.GlyphInstance glyphInstance) {
            commandList.add(new GlyphDrawCommand(glyphInstance));
        }

        @Override
        public void acceptEffect(BakedGlyph glyph, BakedGlyph.Effect effect) {
            var shadowColor = effect.shadowColor();
            if (ARGB.alpha(shadowColor) != 0) {
                commandList.add(createFillRectCommandForEffect(effect, shadowColor));
            }

            var mainColor = effect.color();
            if (ARGB.alpha(mainColor) != 0) {
                commandList.add(createFillRectCommandForEffect(effect, mainColor));
            }
        }

        private FillRectDrawCommand createFillRectCommandForEffect(BakedGlyph.Effect effect, int color) {
            var effectX = effect.left();
            var effectY = effect.top();
            var effectWidth = effect.right() - effectX;
            var effectHeight = effect.bottom() - effectY;

            var red = ARGB.red(color) / 255.0f;
            var green = ARGB.green(color) / 255.0f;
            var blue = ARGB.blue(color) / 255.0f;
            var alpha = ARGB.alpha(color) / 255.0f;

            return new FillRectDrawCommand(effectWidth, effectHeight, red, green, blue, alpha) {
                @Override
                public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
                    var translatedPose = new Matrix4f(pose).translate(effectX, effectY, effect.depth());
                    super.generateVertices(consumer, translatedPose);
                }
            };
        }

        public List<DrawCommand> getCommands() {
            return commandList;
        }
    }
}