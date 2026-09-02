package org.academy.api.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A movable and scalable third-party HUD region exposed in AcademyCraft's HUD editor.
 */
public final class HudLayoutRegion {
    private final String id;
    private final Component name;
    private final float nominalWidth;
    private final float nominalHeight;
    private final HudAnchor defaultAnchor;
    private final float defaultOffsetX;
    private final float defaultOffsetY;
    private final float defaultScale;

    HudLayoutRegion(
            String id,
            Component name,
            float nominalWidth,
            float nominalHeight,
            HudAnchor defaultAnchor,
            float defaultOffsetX,
            float defaultOffsetY,
            float defaultScale
    ) {
        this.id = id;
        this.name = name;
        this.nominalWidth = nominalWidth;
        this.nominalHeight = nominalHeight;
        this.defaultAnchor = defaultAnchor;
        this.defaultOffsetX = defaultOffsetX;
        this.defaultOffsetY = defaultOffsetY;
        this.defaultScale = validScale(defaultScale);
    }

    public String id() {
        return id;
    }

    public Component name() {
        return name;
    }

    public float nominalWidth() {
        return nominalWidth;
    }

    public float nominalHeight() {
        return nominalHeight;
    }

    public float scale() {
        return validScale(defaultScale * state().scale);
    }

    public void setScale(float scale) {
        state().scale = validScale(scale / Math.max(HudLayoutRegistry.MIN_SCALE, defaultScale));
    }

    public Rect rect(Minecraft minecraft) {
        var screenWidth = minecraft.getWindow().getGuiScaledWidth();
        var screenHeight = minecraft.getWindow().getGuiScaledHeight();
        var scale = scale();
        var width = nominalWidth * scale;
        var height = nominalHeight * scale;
        var x = Mth.clamp(baseX(screenWidth, width) + state().offsetX,
                0.0f, Math.max(0.0f, screenWidth - width));
        var y = Mth.clamp(baseY(screenHeight, height) + state().offsetY,
                0.0f, Math.max(0.0f, screenHeight - height));
        return new Rect(x, y, width, height);
    }

    public void setTopLeft(double left, double top, Minecraft minecraft) {
        var current = rect(minecraft);
        var screenWidth = minecraft.getWindow().getGuiScaledWidth();
        var screenHeight = minecraft.getWindow().getGuiScaledHeight();
        var clampedLeft = Mth.clamp(left, 0.0, Math.max(0.0, screenWidth - current.width));
        var clampedTop = Mth.clamp(top, 0.0, Math.max(0.0, screenHeight - current.height));
        var state = state();
        state.offsetX = Math.round((float) clampedLeft - baseX(screenWidth, current.width));
        state.offsetY = Math.round((float) clampedTop - baseY(screenHeight, current.height));
    }

    public void reset() {
        HudLayoutRegistry.reset(id);
    }

    private HudLayoutRegistry.RegionState state() {
        return HudLayoutRegistry.state(id);
    }

    private float baseX(float screenWidth, float width) {
        return switch (defaultAnchor) {
            case TOP_LEFT, CENTER_LEFT -> defaultOffsetX;
            case TOP_RIGHT, CENTER_RIGHT -> screenWidth - width + defaultOffsetX;
            case CENTER -> (screenWidth - width) / 2.0f + defaultOffsetX;
        };
    }

    private float baseY(float screenHeight, float height) {
        return switch (defaultAnchor) {
            case TOP_LEFT, TOP_RIGHT -> defaultOffsetY;
            case CENTER_LEFT, CENTER_RIGHT, CENTER -> (screenHeight - height) / 2.0f + defaultOffsetY;
        };
    }

    private static float validScale(float scale) {
        return Float.isFinite(scale)
                ? Mth.clamp(scale, HudLayoutRegistry.MIN_SCALE, HudLayoutRegistry.MAX_SCALE)
                : 1.0f;
    }

    public record Rect(float x, float y, float width, float height) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
