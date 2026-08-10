package org.academy.internal.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class HudLayoutEditorScreen extends Screen {
    private static final long LONG_PRESS_MS = 200L;
    private static final float HANDLE_SIZE = 9.0f;
    private static final float RESIZE_PIXELS_PER_UNIT = 110.0f;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private static final int DIM = 0x66000000;
    private static final int BOX = 0xFFFFFFFF;
    private static final int BOX_ACTIVE = 0xFF66FFCC;
    private static final int HANDLE = 0xC0FFE34D;
    private static final int TEXT = 0xFFFFFFFF;
    private final @Nullable Screen previousScreen;
    private @Nullable HudLayout.Region grabbed;
    private Mode mode = Mode.NONE;
    private long pressTime;
    private double pressX;
    private double pressY;
    private double grabOffsetX;
    private double grabOffsetY;
    private float initialScale;
    private boolean activated;
    public HudLayoutEditorScreen(@Nullable Screen previousScreen) {
        super(Component.translatable("hud.academy.layout.title"));
        this.previousScreen = previousScreen;
    }

    private static boolean overHandle(
            HudLayout.Region region, HudLayout.Rect rect, double mouseX, double mouseY
    ) {
        var left = rect.x();
        var right = rect.x() + rect.width();
        var bottom = rect.y() + rect.height();
        var handleLeft = usesLeftHandle(region) ? left : right - HANDLE_SIZE;
        return mouseX >= handleLeft && mouseX <= handleLeft + HANDLE_SIZE
                && mouseY >= bottom - HANDLE_SIZE && mouseY <= bottom;
    }

    private static boolean usesLeftHandle(HudLayout.Region region) {
        return region == HudLayout.Region.SKILL_WHEEL;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        var safeWidth = Math.max(1, width);
        var safeHeight = Math.max(1, height);
        graphics.fill(x, y, x + safeWidth, y + 1, color);
        graphics.fill(x, y + safeHeight - 1, x + safeWidth, y + safeHeight, color);
        graphics.fill(x, y, x + 1, y + safeHeight, color);
        graphics.fill(x + safeWidth - 1, y, x + safeWidth, y + safeHeight, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (grabbed != null && !activated && System.currentTimeMillis() - pressTime >= LONG_PRESS_MS) {
            activated = true;
        }

        graphics.centeredText(font, title, width / 2, 8, TEXT);
        graphics.centeredText(font, Component.translatable("hud.academy.layout.hint"), width / 2, 20, 0xFFB0B0B0);

        var minecraft = Minecraft.getInstance();
        for (var region : HudLayout.Region.values()) {
            var rect = region.rect(minecraft);
            var active = grabbed == region && activated;
            var x0 = Math.round(rect.x());
            var y0 = Math.round(rect.y());
            var x1 = Math.round(rect.x() + rect.width());
            var y1 = Math.round(rect.y() + rect.height());
            graphics.fill(x0, y0, x1, y1, active ? 0x3066FFCC : 0x20FFFFFF);
            border(graphics, x0, y0, x1 - x0, y1 - y0, active ? BOX_ACTIVE : BOX);

            var handleX = usesLeftHandle(region) ? x0 : x1 - Math.round(HANDLE_SIZE);
            var handleY = y1 - Math.round(HANDLE_SIZE);
            graphics.fill(handleX, handleY, handleX + Math.round(HANDLE_SIZE), y1, HANDLE);
            border(graphics, handleX, handleY, Math.round(HANDLE_SIZE), Math.round(HANDLE_SIZE), 0xFF000000);

            var label = Component.translatable(region.nameKey()).getString()
                    + "  " + Math.round(region.scale() * 100.0f) + "%";
            graphics.text(font, label, x0 + 2, Math.max(0, y0 + 2), active ? BOX_ACTIVE : TEXT, true);
        }

        var buttonY = height - BUTTON_HEIGHT - 8;
        var resetX = width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        var doneX = width / 2 + BUTTON_GAP / 2;
        drawButton(graphics, resetX, buttonY, Component.translatable("hud.academy.layout.reset"), mouseX, mouseY);
        drawButton(graphics, doneX, buttonY, Component.translatable("hud.academy.layout.done"), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        var mouseX = event.x();
        var mouseY = event.y();
        var buttonY = height - BUTTON_HEIGHT - 8;
        var resetX = width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        var doneX = width / 2 + BUTTON_GAP / 2;
        if (inside(mouseX, mouseY, resetX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            HudLayout.resetAll();
            HudLayoutConfig.save();
            return true;
        }
        if (inside(mouseX, mouseY, doneX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }

        var minecraft = Minecraft.getInstance();
        for (var region : HudLayout.Region.values()) {
            var rect = region.rect(minecraft);
            if (overHandle(region, rect, mouseX, mouseY)) {
                beginGrab(region, Mode.RESIZE, rect, mouseX, mouseY);
                return true;
            }
        }
        for (var region : HudLayout.Region.values()) {
            var rect = region.rect(minecraft);
            if (rect.contains(mouseX, mouseY)) {
                beginGrab(region, Mode.MOVE, rect, mouseX, mouseY);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && grabbed != null) {
            if (!activated && System.currentTimeMillis() - pressTime >= LONG_PRESS_MS) activated = true;
            if (!activated) return true;
            if (mode == Mode.MOVE) {
                grabbed.setTopLeft(event.x() - grabOffsetX, event.y() - grabOffsetY, Minecraft.getInstance());
            } else if (mode == Mode.RESIZE) {
                var horizontalDelta = usesLeftHandle(grabbed)
                        ? pressX - event.x()
                        : event.x() - pressX;
                var delta = (horizontalDelta + event.y() - pressY) / 2.0;
                grabbed.setScale(initialScale + (float) (delta / RESIZE_PIXELS_PER_UNIT));
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && grabbed != null) {
            if (activated) HudLayoutConfig.save();
            grabbed = null;
            mode = Mode.NONE;
            activated = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        HudLayoutConfig.save();
        Minecraft.getInstance().gui.setScreen(previousScreen);
    }

    private void beginGrab(
            HudLayout.Region region, Mode mode, HudLayout.Rect rect, double mouseX, double mouseY
    ) {
        grabbed = region;
        this.mode = mode;
        pressTime = System.currentTimeMillis();
        pressX = mouseX;
        pressY = mouseY;
        grabOffsetX = mouseX - rect.x();
        grabOffsetY = mouseY - rect.y();
        initialScale = region.scale();
        activated = false;
    }

    private void drawButton(
            GuiGraphicsExtractor graphics, int x, int y, Component label, int mouseX, int mouseY
    ) {
        var hovered = inside(mouseX, mouseY, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? 0x503E9FB0 : 0x40111111);
        border(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? BOX_ACTIVE : BOX);
        graphics.centeredText(font, label, x + BUTTON_WIDTH / 2, y + 6, TEXT);
    }

    private enum Mode {NONE, MOVE, RESIZE}
}
