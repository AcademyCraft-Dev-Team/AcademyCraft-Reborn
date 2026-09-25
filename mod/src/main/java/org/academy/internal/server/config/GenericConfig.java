package org.academy.internal.server.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.academy.api.common.gson.TypeHandler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-wide general settings ({@code academy-server.json} 的 "generic" 段)。
 *
 * <p>每一段都对应一处真实生效的服务端行为；新增开关时必须同时接入实现，避免出现只写不进逻辑的死配置。</p>
 */
public class GenericConfig {
    public static final String KEY = "generic";

    @SerializedName("general")
    public final GeneralSettings general = new GeneralSettings();

    @SerializedName("worldgen")
    public final WorldgenSettings worldgen = new WorldgenSettings();

    @SerializedName("friendlyFire")
    public final FriendlyFireSettings friendlyFire = new FriendlyFireSettings();

    /**
     * 全局玩法总开关。
     */
    public static class GeneralSettings {
        /**
         * 服务端总开关：关闭后所有技能都无法破坏方块，客户端无法覆盖。
         */
        @SerializedName("blockDestruction")
        public boolean blockDestruction = true;

        /**
         * 服务端总开关：关闭后玩家之间的一切技能交互都会被拒绝。
         */
        @SerializedName("pvp")
        public boolean pvp = true;

        /**
         * 服务器启动时的开发者模式初值；可用命令临时切换。
         */
        @SerializedName("devMode")
        public boolean devMode = false;
    }

    /**
     * 世界生成开关。
     */
    public static class WorldgenSettings {
        /**
         * 是否生成想象相位湖（academy:lake_imag_phase）。
         */
        @SerializedName("generateImagPhaseLakes")
        public boolean generateImagPhaseLakes = true;
    }

    /**
     * 友伤相关设置。
     */
    public static class FriendlyFireSettings {
        /**
         * CTA 友伤保护名单：{@code tamed} 表示已驯服生物，{@code tag:<id>} 表示实体标签，
         * 其余按实体 ID 匹配。
         */
        @SerializedName("ctaWhitelist")
        public final List<String> ctaWhitelist = new ArrayList<>();
    }

    /**
     * Reads the pre-typed layout ({@code booleanMap}/{@code stringListMap}) so existing files keep
     * their behavior: {@code destroyBlocks} maps to the global gate and
     * {@code ctaFriendlyFireWhitelist} to the friendly-fire list. A typed key explicitly present in
     * the file always wins; legacy values only fill keys the file does not define.
     */
    void migrateLegacyKeys(JsonObject root) {
        var booleanMap = objectAt(root, "booleanMap");
        if (booleanMap != null) {
            if (booleanMap.has("destroyBlocks") && !generalHas(root, "blockDestruction")) {
                general.blockDestruction = booleanMap.get("destroyBlocks").getAsBoolean();
            }
            if (booleanMap.has("attackPlayer") && !generalHas(root, "pvp")) {
                general.pvp = booleanMap.get("attackPlayer").getAsBoolean();
            }
            if (booleanMap.has("devMode") && !generalHas(root, "devMode")) {
                general.devMode = booleanMap.get("devMode").getAsBoolean();
            }
            if (booleanMap.has("genPhaseLiquid") && !worldgenHas(root, "generateImagPhaseLakes")) {
                worldgen.generateImagPhaseLakes = booleanMap.get("genPhaseLiquid").getAsBoolean();
            }
        }
        var stringListMap = objectAt(root, "stringListMap");
        if (stringListMap != null && !friendlyFireHas(root, "ctaWhitelist")
                && stringListMap.has("ctaFriendlyFireWhitelist")
                && stringListMap.get("ctaFriendlyFireWhitelist").isJsonArray()) {
            friendlyFire.ctaWhitelist.clear();
            for (var element : stringListMap.getAsJsonArray("ctaFriendlyFireWhitelist")) {
                friendlyFire.ctaWhitelist.add(element.getAsString());
            }
        }
    }

    private static boolean generalHas(JsonObject root, String key) {
        return sectionHas(root, "general", key);
    }

    private static boolean worldgenHas(JsonObject root, String key) {
        return sectionHas(root, "worldgen", key);
    }

    private static boolean friendlyFireHas(JsonObject root, String key) {
        return sectionHas(root, "friendlyFire", key);
    }

    private static boolean sectionHas(JsonObject root, String section, String key) {
        var object = objectAt(root, section);
        return object != null && object.has(key);
    }

    private static @Nullable JsonObject objectAt(JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null;
    }

    public static final class Action implements TypeHandler<GenericConfig> {
        public static final TypeHandler<GenericConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public GenericConfig getDefault() {
            var defaultConfig = new GenericConfig();
            defaultConfig.friendlyFire.ctaWhitelist.add("tamed");
            defaultConfig.friendlyFire.ctaWhitelist.add("touhou_little_maid:maid");
            return defaultConfig;
        }

        @Override
        public TypeAdapter<GenericConfig> getAdapter(Gson gson) {
            var delegate = gson.getAdapter(GenericConfig.class);
            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, GenericConfig value)
                        throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public GenericConfig read(JsonReader in)
                        throws IOException {
                    var element = JsonParser.parseReader(in);
                    var config = delegate.fromJsonTree(element);
                    if (config != null && element.isJsonObject()) {
                        config.migrateLegacyKeys(element.getAsJsonObject());
                    }
                    return config;
                }
            };
        }

        @Override
        public Class<GenericConfig> getTypeClass() {
            return GenericConfig.class;
        }
    }
}
