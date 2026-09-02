package org.academy.api.client.hud;

import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.common.gson.TypeHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for HUD regions supplied by other mods.
 * Registered regions are included in AcademyCraft's data-terminal HUD editor and
 * their player offsets/scales are persisted in the AcademyCraft client config.
 */
public final class HudLayoutRegistry {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;
    public static final String CONFIG_KEY = "external_hud_layout";

    private static final Map<String, HudLayoutRegion> REGIONS = new LinkedHashMap<>();
    private static Config config;

    private HudLayoutRegistry() {
    }

    public static synchronized HudLayoutRegion register(
            String id,
            Component name,
            float nominalWidth,
            float nominalHeight,
            HudAnchor defaultAnchor,
            float defaultOffsetX,
            float defaultOffsetY,
            float defaultScale
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        if (id.isBlank()) throw new IllegalArgumentException("HUD region id cannot be blank");
        if (!Float.isFinite(nominalWidth) || nominalWidth <= 0.0f
                || !Float.isFinite(nominalHeight) || nominalHeight <= 0.0f) {
            throw new IllegalArgumentException("HUD region size must be finite and positive");
        }
        var existing = REGIONS.get(id);
        if (existing != null) return existing;
        var region = new HudLayoutRegion(
                id, name, nominalWidth, nominalHeight, defaultAnchor,
                defaultOffsetX, defaultOffsetY, defaultScale
        );
        REGIONS.put(id, region);
        state(id);
        return region;
    }

    public static synchronized List<HudLayoutRegion> regions() {
        return List.copyOf(new ArrayList<>(REGIONS.values()));
    }

    public static synchronized void resetAll() {
        for (var region : REGIONS.values()) region.reset();
    }

    public static synchronized void save() {
        AcademyCraftClient.Config.INSTANCE.setConfig(CONFIG_KEY, config());
        AcademyCraftClient.Config.INSTANCE.save();
    }

    static synchronized RegionState state(String id) {
        return config().regions.computeIfAbsent(id, ignored -> new RegionState());
    }

    static synchronized void reset(String id) {
        config().regions.remove(id);
    }

    private static Config config() {
        if (config == null) {
            AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, Config.Action.INSTANCE);
            config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY);
        }
        return config;
    }

    static final class RegionState {
        @SerializedName("offsetX")
        int offsetX;
        @SerializedName("offsetY")
        int offsetY;
        @SerializedName("scale")
        float scale = 1.0f;
    }

    public static final class Config {
        @SerializedName("regions")
        Map<String, RegionState> regions = new LinkedHashMap<>();

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
