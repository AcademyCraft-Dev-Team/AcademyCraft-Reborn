package org.academy.internal.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class HudLayout {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    private static final float TOGGLE_STATUS_WIDTH = 140.0f;
    private static final float TOGGLE_STATUS_HEIGHT = 75.0f;
    private static final float MENTAL_CONTROL_WIDTH = 168.0f;
    private static final float MENTAL_CONTROL_HEIGHT = 184.0f;
    private static final float CP_WIDTH = 240.0f;
    private static final float CP_HEIGHT = 27.0f;
    private static final float SKILL_WHEEL_WIDTH = 104.0f;
    private static final float SKILL_WHEEL_HEIGHT = 119.0f;

    private HudLayout() {
    }

    public static void resetAll() {
        for (var region : Region.values()) region.reset();
    }

    private static float validScale(float scale) {
        return Float.isFinite(scale) ? Mth.clamp(scale, MIN_SCALE, MAX_SCALE) : 1.0f;
    }

    public enum Region {
        TOGGLE_STATUS("toggle_status", "hud.academy.layout.region.ability_status"),
        MENTAL_CONTROL("mental_control", "hud.academy.layout.region.mental_control"),
        CP("cp", "hud.academy.layout.region.cp"),
        SKILL_WHEEL("skill_wheel", "hud.academy.layout.region.skill_name");

        private final String configKey;
        private final String nameKey;

        Region(String configKey, String nameKey) {
            this.configKey = configKey;
            this.nameKey = nameKey;
        }

        public String configKey() {
            return configKey;
        }

        public String nameKey() {
            return nameKey;
        }

        public float scale() {
            var config = HudLayoutConfig.get();
            var userScale = switch (this) {
                case TOGGLE_STATUS -> validScale(config.toggleStatusHudScale);
                case MENTAL_CONTROL -> validScale(config.mentalControlHudScale);
                case CP -> validScale(config.cpHudScale);
                case SKILL_WHEEL -> validScale(config.skillWheelHudScale);
            };
            return validScale(HudLayoutDefaults.INSTANCE.region(configKey).getScale() * userScale);
        }

        public void setScale(float scale) {
            var baseScale = HudLayoutDefaults.INSTANCE.region(configKey).getScale();
            var value = Mth.clamp(scale / Math.max(MIN_SCALE, baseScale), MIN_SCALE, MAX_SCALE);
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> config.toggleStatusHudScale = value;
                case MENTAL_CONTROL -> config.mentalControlHudScale = value;
                case CP -> config.cpHudScale = value;
                case SKILL_WHEEL -> config.skillWheelHudScale = value;
            }
        }

        public Rect rect(Minecraft minecraft) {
            return rect(minecraft, HudLayoutDefaults.INSTANCE.get(), true);
        }

        public Rect rect(Minecraft minecraft, HudLayoutDefaults.Config defaults, boolean includePlayerConfig) {
            var screenWidth = minecraft.getWindow().getGuiScaledWidth();
            var screenHeight = minecraft.getWindow().getGuiScaledHeight();
            var defaultsValue = defaults.getRegions().get(configKey);
            if (defaultsValue == null) defaultsValue = HudLayoutDefaults.INSTANCE.defaults().getRegions().get(configKey);
            var userScale = includePlayerConfig ? userScale() : 1.0f;
            var scale = validScale(defaultsValue.getScale() * userScale);
            var width = nominalWidth() * scale;
            var height = nominalHeight() * scale;
            var x = switch (defaultsValue.getAnchor()) {
                case TOP_LEFT, CENTER_LEFT -> defaultsValue.getOffsetX();
                case TOP_RIGHT, CENTER_RIGHT -> screenWidth - width + defaultsValue.getOffsetX();
            };
            var y = switch (defaultsValue.getAnchor()) {
                case TOP_LEFT, TOP_RIGHT -> defaultsValue.getOffsetY();
                case CENTER_LEFT, CENTER_RIGHT -> (screenHeight - height) / 2.0f + defaultsValue.getOffsetY();
            };
            if (includePlayerConfig) {
                x += offsetX();
                y += offsetY();
            }
            return new Rect(x, y, width, height);
        }

        public void setTopLeft(double left, double top, Minecraft minecraft) {
            var current = rect(minecraft);
            var screenWidth = minecraft.getWindow().getGuiScaledWidth();
            var screenHeight = minecraft.getWindow().getGuiScaledHeight();
            var clampedLeft = Mth.clamp(left, 0.0, Math.max(0.0, screenWidth - current.width));
            var clampedTop = Mth.clamp(top, 0.0, Math.max(0.0, screenHeight - current.height));
            var defaultsValue = HudLayoutDefaults.INSTANCE.region(configKey);
            var baseX = switch (defaultsValue.getAnchor()) {
                case TOP_LEFT, CENTER_LEFT -> defaultsValue.getOffsetX();
                case TOP_RIGHT, CENTER_RIGHT -> screenWidth - current.width + defaultsValue.getOffsetX();
            };
            var baseY = switch (defaultsValue.getAnchor()) {
                case TOP_LEFT, TOP_RIGHT -> defaultsValue.getOffsetY();
                case CENTER_LEFT, CENTER_RIGHT -> (screenHeight - current.height) / 2.0f
                        + defaultsValue.getOffsetY();
            };
            setOffsets(
                    Math.round((float) clampedLeft - baseX),
                    Math.round((float) clampedTop - baseY)
            );
        }

        public void reset() {
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> {
                    config.toggleStatusHudOffsetX = 0;
                    config.toggleStatusHudOffsetY = 0;
                    config.toggleStatusHudScale = 1.0f;
                }
                case MENTAL_CONTROL -> {
                    config.mentalControlHudOffsetX = 0;
                    config.mentalControlHudOffsetY = 0;
                    config.mentalControlHudScale = 1.0f;
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

        public float nominalWidth() {
            return switch (this) {
                case TOGGLE_STATUS -> TOGGLE_STATUS_WIDTH;
                case MENTAL_CONTROL -> MENTAL_CONTROL_WIDTH;
                case CP -> CP_WIDTH;
                case SKILL_WHEEL -> SKILL_WHEEL_WIDTH;
            };
        }

        public float nominalHeight() {
            return switch (this) {
                case TOGGLE_STATUS -> TOGGLE_STATUS_HEIGHT;
                case MENTAL_CONTROL -> MENTAL_CONTROL_HEIGHT;
                case CP -> CP_HEIGHT;
                case SKILL_WHEEL -> SKILL_WHEEL_HEIGHT;
            };
        }

        private float userScale() {
            var config = HudLayoutConfig.get();
            return switch (this) {
                case TOGGLE_STATUS -> validScale(config.toggleStatusHudScale);
                case MENTAL_CONTROL -> validScale(config.mentalControlHudScale);
                case CP -> validScale(config.cpHudScale);
                case SKILL_WHEEL -> validScale(config.skillWheelHudScale);
            };
        }

        private int offsetX() {
            var config = HudLayoutConfig.get();
            return switch (this) {
                case TOGGLE_STATUS -> config.toggleStatusHudOffsetX;
                case MENTAL_CONTROL -> config.mentalControlHudOffsetX;
                case CP -> config.cpHudOffsetX;
                case SKILL_WHEEL -> config.skillWheelHudOffsetX;
            };
        }

        private int offsetY() {
            var config = HudLayoutConfig.get();
            return switch (this) {
                case TOGGLE_STATUS -> config.toggleStatusHudOffsetY;
                case MENTAL_CONTROL -> config.mentalControlHudOffsetY;
                case CP -> config.cpHudOffsetY;
                case SKILL_WHEEL -> config.skillWheelHudOffsetY;
            };
        }

        private void setOffsets(int x, int y) {
            var config = HudLayoutConfig.get();
            switch (this) {
                case TOGGLE_STATUS -> {
                    config.toggleStatusHudOffsetX = x;
                    config.toggleStatusHudOffsetY = y;
                }
                case MENTAL_CONTROL -> {
                    config.mentalControlHudOffsetX = x;
                    config.mentalControlHudOffsetY = y;
                }
                case CP -> {
                    config.cpHudOffsetX = x;
                    config.cpHudOffsetY = y;
                }
                case SKILL_WHEEL -> {
                    config.skillWheelHudOffsetX = x;
                    config.skillWheelHudOffsetY = y;
                }
            }
        }
    }

    public record Rect(float x, float y, float width, float height) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
