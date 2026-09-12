package org.academy.internal.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.academy.api.client.hud.HudManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HudLayoutEditorScreen extends Screen {
    private static final float SCALE_STEP = 0.05f;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private static final int DIM = 0x66000000;
    private static final int BOX = 0xFFFFFFFF;
    private static final int BOX_ACTIVE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0x99FFFFFF;
    private static final int HIDDEN_BOX = 0x66FFFFFF;
    private static final int HIDDEN_FILL = 0x0CFFFFFF;
    private static final int FILL = 0x18FFFFFF;
    private static final int FILL_ACTIVE = 0x30FFFFFF;

    private final @Nullable Screen previousScreen;
    private @Nullable HudLayout.Region grabbed;
    private double grabStartX;
    private double grabStartY;
    private float initialTranslateX;
    private float initialTranslateY;

    public HudLayoutEditorScreen(@Nullable Screen previousScreen) {
        super(Component.translatable("hud.academy.layout.title"));
        this.previousScreen = previousScreen;
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
        graphics.centeredText(font, title, width / 2, 8, TEXT);
        graphics.centeredText(font, Component.translatable("hud.academy.layout.hint"), width / 2, 20, TEXT_DIM);

        var minecraft = Minecraft.getInstance();
        var hiddenLabel = Component.translatable("hud.academy.layout.hidden").getString();
        for (var region : editableRegions()) {
            var rect = region.rect(minecraft);
            var active = region == grabbed;
            var hidden = region.getHidden();
            var x0 = Math.round(rect.getX());
            var y0 = Math.round(rect.getY());
            var x1 = Math.round(rect.getX() + rect.getWidth());
            var y1 = Math.round(rect.getY() + rect.getHeight());
            graphics.fill(x0, y0, x1, y1, hidden ? HIDDEN_FILL : (active ? FILL_ACTIVE : FILL));
            border(graphics, x0, y0, x1 - x0, y1 - y0, hidden ? HIDDEN_BOX : (active ? BOX_ACTIVE : BOX));

            var label = Component.translatable(region.getNameKey()).getString()
                    + "  " + Math.round(region.getScaleXY() * 100.0f)
                    + "%" + (hidden ? "  " + hiddenLabel : "");
            graphics.text(font, label, x0 + 2, Math.max(0, y0 + 2), hidden ? TEXT_DIM : (active ? BOX_ACTIVE : TEXT), true);
        }

        var buttonY = height - BUTTON_HEIGHT - 8;
        var resetX = width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        var doneX = width / 2 + BUTTON_GAP / 2;
        drawButton(graphics, resetX, buttonY, Component.translatable("hud.academy.layout.reset"), mouseX, mouseY);
        drawButton(graphics, doneX, buttonY, Component.translatable("hud.academy.layout.done"), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var mouseX = event.x();
        var mouseY = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            var buttonY = height - BUTTON_HEIGHT - 8;
            var resetX = width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
            var doneX = width / 2 + BUTTON_GAP / 2;
            if (inside(mouseX, mouseY, resetX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                HudLayout.resetAll();
                saveAndRebuild();
                return true;
            }
            if (inside(mouseX, mouseY, doneX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                onClose();
                return true;
            }
        }

        var region = regionAt(mouseX, mouseY);
        if (region == null) return true;

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            region.toggleHidden();
            saveAndRebuild();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            grabbed = region;
            grabStartX = mouseX;
            grabStartY = mouseY;
            initialTranslateX = region.getTranslateX();
            initialTranslateY = region.getTranslateY();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && grabbed != null) {
            var deltaX = (float) (event.x() - grabStartX);
            var deltaY = (float) (event.y() - grabStartY);
            grabbed.setTranslate(initialTranslateX + deltaX, initialTranslateY + deltaY, Minecraft.getInstance());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && grabbed != null) {
            saveAndRebuild();
            grabbed = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        var region = regionAt(mouseX, mouseY);
        if (region == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        var next = region.getScaleXY() + (scrollY > 0.0 ? SCALE_STEP : -SCALE_STEP);
        region.setScaleXY(next);
        saveAndRebuild();
        return true;
    }

    @Override
    public void onClose() {
        saveAndRebuild();
        Minecraft.getInstance().gui.setScreen(previousScreen);
    }

    private void saveAndRebuild() {
        HudLayoutConfig.save();
        HudManager.INSTANCE.rebuildHudLayouts();
    }

    private @Nullable HudLayout.Region regionAt(double mouseX, double mouseY) {
        var minecraft = Minecraft.getInstance();
        var regions = editableRegions();
        for (var i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.rect(minecraft).contains(mouseX, mouseY)) return region;
        }
        return null;
    }

    private void drawButton(
            GuiGraphicsExtractor graphics, int x, int y, Component label, int mouseX, int mouseY
    ) {
        var hovered = inside(mouseX, mouseY, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? 0x40FFFFFF : 0x20FFFFFF);
        border(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? BOX_ACTIVE : BOX);
        graphics.centeredText(font, label, x + BUTTON_WIDTH / 2, y + 6, TEXT);
    }

    private static List<HudLayout.Region> editableRegions() {
        return new ArrayList<>(Arrays.asList(HudLayout.Region.values()));
    }
}
