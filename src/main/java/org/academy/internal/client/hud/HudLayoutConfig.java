package org.academy.internal.client.hud;

import com.google.gson.annotations.SerializedName;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.common.gson.TypeHandler;

public final class HudLayoutConfig {
    public static final String CONFIG_KEY = "hud_layout";

    private static Config config;

    private HudLayoutConfig() {
    }

    public static void init() {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE);
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);
    }

    public static Config get() {
        if (config == null) init();
        return config;
    }

    public static void save() {
        AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, get());
        AcademyCraftClient.Config.INSTANCE.save();
    }

    public static final class Config {
        @SerializedName("toggleStatusHudOffsetX")
        public int toggleStatusHudOffsetX;
        @SerializedName("toggleStatusHudOffsetY")
        public int toggleStatusHudOffsetY;
        @SerializedName("toggleStatusHudScale")
        public float toggleStatusHudScale = 1.0f;

        @SerializedName("cpHudOffsetX")
        public int cpHudOffsetX;
        @SerializedName("cpHudOffsetY")
        public int cpHudOffsetY;
        @SerializedName("cpHudScale")
        public float cpHudScale = 1.0f;

        @SerializedName("skillWheelHudOffsetX")
        public int skillWheelHudOffsetX;
        @SerializedName("skillWheelHudOffsetY")
        public int skillWheelHudOffsetY;
        @SerializedName("skillWheelHudScale")
        public float skillWheelHudScale = 1.0f;

        public static final class Action implements TypeHandler<Config> {
            public static final TypeHandler<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public Config getDefault() {
                return new Config();
            }

            @Override
            public Class<Config> getTypeClass() {
                return Config.class;
            }
        }
    }
}
