package org.academy.api.client.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.GlyphDrawCommand;

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

        public List<DrawCommand> getCommands() {
            return commandList;
        }

        @Override
        public void acceptGlyph(TextRenderable textRenderable) {
            commandList.add(new GlyphDrawCommand(textRenderable));
        }

        @Override
        public void acceptEffect(TextRenderable textRenderable) {
            commandList.add(new GlyphDrawCommand(textRenderable));
        }
    }
}