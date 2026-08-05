package org.academy.internal.client.hud;

import net.minecraft.client.Minecraft;

public final class HudLayout {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    private static final float TOGGLE_STATUS_WIDTH = 140.0f;
    private static final float TOGGLE_STATUS_HEIGHT = 75.0f;
    private static final float CP_WIDTH = 240.0f;
    private static final float CP_HEIGHT = 27.0f;
    private static final float SKILL_WHEEL_WIDTH = 104.0f;
    private static final float SKILL_WHEEL_HEIGHT = 119.0f;

    private HudLayout() {
    }

    public record Rect(float x, float y, float width, float height) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    public enum Region {
        TOGGLE_STATUS("hud.academy.layout.region.ability_status"),
        CP("hud.academy.layout.region.cp"),
        SKILL_WHEEL("hud.academy.layout.region.skill_name");

        private final String nameKey;

        Region(String nameKey) {
            this.nameKey = nameKey;
        }

        public String nameKey() {
            return nameKey;
        }

        public float scale() {
            var config = HudLayoutConfig.get();
            return switch (this) {
                case TOGGLE_STATUS -> validScale(config.toggleStatusHudScale);
                case CP -> validScale(config.cpHudScale);
                case SKILL_WHEEL -> validScale(config.skillWheelHudScale);
            };
        }

        public void setScale(float scale) {
            var value = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> config.toggleStatusHudScale = value;
                case CP -> config.cpHudScale = value;
                case SKILL_WHEEL -> config.skillWheelHudScale = value;
            }
        }

        public Rect rect(Minecraft minecraft) {
            var screenWidth = minecraft.getWindow().getGuiScaledWidth();
            var screenHeight = minecraft.getWindow().getGuiScaledHeight();
            var scale = scale();
            var width = nominalWidth() * scale;
            var height = nominalHeight() * scale;
            var config = HudLayoutConfig.get();
            return switch (this) {
                case TOGGLE_STATUS -> new Rect(
                        8.0f + config.toggleStatusHudOffsetX,
                        8.0f + config.toggleStatusHudOffsetY,
                        width,
                        height
                );
                case CP -> new Rect(
                        screenWidth - 4.0f - width + config.cpHudOffsetX,
                        4.0f + config.cpHudOffsetY,
                        width,
                        height
                );
                case SKILL_WHEEL -> new Rect(
                        screenWidth - width + config.skillWheelHudOffsetX,
                        (screenHeight - height) / 2.0f + config.skillWheelHudOffsetY,
                        width,
                        height
                );
            };
        }

        public void setTopLeft(double left, double top, Minecraft minecraft) {
            var current = rect(minecraft);
            var screenWidth = minecraft.getWindow().getGuiScaledWidth();
            var screenHeight = minecraft.getWindow().getGuiScaledHeight();
            var clampedLeft = Math.clamp(left, 0.0, Math.max(0.0, screenWidth - current.width));
            var clampedTop = Math.clamp(top, 0.0, Math.max(0.0, screenHeight - current.height));
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> {
                    config.toggleStatusHudOffsetX = Math.round((float) clampedLeft - 8.0f);
                    config.toggleStatusHudOffsetY = Math.round((float) clampedTop - 8.0f);
                }
                case CP -> {
                    config.cpHudOffsetX = Math.round((float) clampedLeft - (screenWidth - 4.0f - current.width));
                    config.cpHudOffsetY = Math.round((float) clampedTop - 4.0f);
                }
                case SKILL_WHEEL -> {
                    config.skillWheelHudOffsetX = Math.round((float) clampedLeft - (screenWidth - current.width));
                    config.skillWheelHudOffsetY = Math.round(
                            (float) clampedTop - (screenHeight - current.height) / 2.0f
                    );
                }
            }
        }

        public void reset() {
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> {
                    config.toggleStatusHudOffsetX = 0;
                    config.toggleStatusHudOffsetY = 0;
                    config.toggleStatusHudScale = 1.0f;
                }
                case CP -> {
                    config.cpHudOffsetX = 0;
                    config.cpHudOffsetY = 0;
                    config.cpHudScale = 1.0f;
                }
                case SKILL_WHEEL -> {
                    config.skillWheelHudOffsetX = 0;
                    config.skillWheelHudOffsetY = 0;
                    config.skillWheelHudScale = 1.0f;
                }
            }
        }

        private float nominalWidth() {
            return switch (this) {
                case TOGGLE_STATUS -> TOGGLE_STATUS_WIDTH;
                case CP -> CP_WIDTH;
                case SKILL_WHEEL -> SKILL_WHEEL_WIDTH;
            };
        }

        private float nominalHeight() {
            return switch (this) {
                case TOGGLE_STATUS -> TOGGLE_STATUS_HEIGHT;
                case CP -> CP_HEIGHT;
                case SKILL_WHEEL -> SKILL_WHEEL_HEIGHT;
            };
        }
    }

    public static void resetAll() {
        for (var region : Region.values()) region.reset();
    }

    private static float validScale(float scale) {
        return Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1.0f;
    }
}
