package org.academy.internal.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared visual tokens for screens that belong to the data-terminal interface family.
 * The palette intentionally stays neutral and warm so skill UIs do not fall back to
 * the old cyan/blue treatment.
 */
public final class DataTerminalTheme {
    public static final int PANEL_BACKGROUND = 0xD9141412;
    public static final int SECTION_BACKGROUND = 0xB81F1E1A;
    public static final int CANVAS_BACKGROUND = 0xE00D0D0C;
    public static final int CONTROL_BACKGROUND = 0x70292722;
    public static final int INPUT_BACKGROUND = 0xC0181714;
    public static final int ROW_BACKGROUND = 0x70272521;
    public static final int ROW_ALTERNATE = 0x60302D27;
    public static final int EMPTY_BACKGROUND = 0x38272521;
    public static final int HEALTH_BACKGROUND = 0x80403C34;

    public static final int BORDER = 0xD9E9E4D7;
    public static final int BORDER_MUTED = 0x66D5CFC0;
    public static final int DIVIDER = 0x99E0DACC;
    public static final int TEXT = 0xFFF4F0E6;
    public static final int TEXT_DIM = 0xFFB8B1A3;
    public static final int TEXT_MUTED = 0xFF8D877C;
    public static final int TEXT_DISABLED = 0xFF68635B;
    public static final int ACCENT = 0xFFE0B84F;
    public static final int HOVER = 0x30FFFFFF;
    public static final int SELECTED = 0x55E0B84F;
    public static final int FILTER_ACCENT = 0xFFD4D7DC;
    public static final int FILTER_SELECTED = 0x556A7078;
    public static final int TELEPORT_ACCENT = 0xFFC77DFF;
    public static final int TELEPORT_SELECTED = 0x557B3FA1;
    public static final int GOOD = 0xFF79C58D;
    public static final int WARNING = 0xFFD8A84B;
    public static final int DANGER = 0xFFE16F68;

    private DataTerminalTheme() {
    }

    public static void panel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int headerHeight
    ) {
        panel(graphics, x, y, width, height, headerHeight, ACCENT);
    }

    public static void panel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int headerHeight,
            int accent
    ) {
        graphics.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        border(graphics, x, y, width, height, BORDER);
        if (headerHeight > 0) {
            graphics.fill(x + 7, y + 6, x + 9, y + Math.min(headerHeight - 4, 14), accent);
            graphics.fill(x + 7, y + headerHeight, x + width - 7, y + headerHeight + 1, DIVIDER);
        }
    }

    public static void section(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SECTION_BACKGROUND);
        border(graphics, x, y, width, height, BORDER_MUTED);
    }

    public static void input(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean focused) {
        input(graphics, x, y, width, height, focused, ACCENT);
    }

    public static void input(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean focused,
            int accent
    ) {
        graphics.fill(x, y, x + width, y + height, INPUT_BACKGROUND);
        border(graphics, x, y, width, height, focused ? accent : BORDER_MUTED);
        if (focused) graphics.fill(x, y, x + 2, y + height, accent);
    }

    public static void button(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean enabled,
            boolean selected,
            boolean hovered
    ) {
        button(graphics, x, y, width, height, enabled, selected, hovered, ACCENT, SELECTED);
    }

    public static void button(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean enabled,
            boolean selected,
            boolean hovered,
            int accent,
            int selectedBackground
    ) {
        var background = !enabled ? 0x30120F0C
                : selected ? selectedBackground
                : hovered ? HOVER : CONTROL_BACKGROUND;
        graphics.fill(x, y, x + width, y + height, background);
        border(graphics, x, y, width, height, selected || hovered ? accent : BORDER_MUTED);
        if (selected) graphics.fill(x, y, x + 2, y + height, accent);
    }

    public static void scrollBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int scroll,
            int maxScroll
    ) {
        scrollBar(graphics, x, y, width, height, scroll, maxScroll, ACCENT);
    }

    public static void scrollBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int scroll,
            int maxScroll,
            int accent
    ) {
        graphics.fill(x, y, x + width, y + height, CONTROL_BACKGROUND);
        if (maxScroll <= 0) {
            graphics.fill(x, y, x + width, y + height, BORDER_MUTED);
            return;
        }
        var thumbHeight = Math.max(10, height / 4);
        var thumbY = y + (int) ((height - thumbHeight) * (scroll / (float) maxScroll));
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, accent);
    }

    public static void border(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
