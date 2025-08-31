package org.academy.api.client.hud.terminal;

import com.google.gson.annotations.SerializedName;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.common.gson.TypeHandler;
import org.jetbrains.annotations.NotNull;

public class Config extends KeyBindingConfig {
    @SerializedName("layout")
    public LayoutConfig layout = new LayoutConfig();
    @SerializedName("blurRadius")
    public float blurRadius = 10.0F;
    @SerializedName("enableBlur")
    public boolean enableBlur = true;
    @SerializedName("mouseSensitivity")
    public float mouseSensitivity = 1.0F;

    public static class LayoutConfig {
        @SerializedName("scale")
        public float scale = 0.9F;
        @SerializedName("cursorWidgetSize")
        public float cursorWidgetSize = 4.0F;
    }

    public static final class Action implements TypeHandler<Config> {
        public static final TypeHandler<Config> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public @NotNull Config getDefault() {
            return new Config();
        }

        @Override
        public @NotNull Class<Config> getTypeClass() {
            return Config.class;
        }
    }
}