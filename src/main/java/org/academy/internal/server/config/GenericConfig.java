package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

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

    @SerializedName("structureRestoration")
    public final StructureRestorationSettings structureRestoration = new StructureRestorationSettings();

    /** Skill-created structures only; ordinary or unmarked saved structures remain untouched. */
    public static class StructureRestorationSettings {
        public boolean enabled = true;
        public boolean allowOfflineOwners = true;
        public int restTicks = 100;
        public int checkIntervalTicks = 20;
        public int candidatesPerTick = 4;
        public int retryTicks = 100;
    }

    /** 全局玩法总开关。 */
    public static class GeneralSettings {
        /** 服务端总开关：关闭后所有技能都无法破坏方块，客户端无法覆盖。 */
        @SerializedName("blockDestruction")
        public boolean blockDestruction = true;

        /** 服务端总开关：关闭后玩家之间的一切技能交互都会被拒绝。 */
        @SerializedName("pvp")
        public boolean pvp = true;

        /** 服务器启动时的开发者模式初值；可用命令临时切换。 */
        @SerializedName("devMode")
        public boolean devMode = false;
    }

    /** 世界生成开关。 */
    public static class WorldgenSettings {
        /** 是否生成想象相位湖（academy:lake_imag_phase）。 */
        @SerializedName("generateImagPhaseLakes")
        public boolean generateImagPhaseLakes = true;
    }

    /** 友伤相关设置。 */
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
    void migrateLegacyKeys(com.google.gson.JsonObject root) {
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

    private static boolean generalHas(com.google.gson.JsonObject root, String key) {
        return sectionHas(root, "general", key);
    }

    private static boolean worldgenHas(com.google.gson.JsonObject root, String key) {
        return sectionHas(root, "worldgen", key);
    }

    private static boolean friendlyFireHas(com.google.gson.JsonObject root, String key) {
        return sectionHas(root, "friendlyFire", key);
    }

    private static boolean sectionHas(com.google.gson.JsonObject root, String section, String key) {
        var object = objectAt(root, section);
        return object != null && object.has(key);
    }

    private static com.google.gson.JsonObject objectAt(com.google.gson.JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null;
    }

    /** CP gained per 1 MSk of Misaka compute (1 MSk : 2 CP by default). */
    @SerializedName("misakaCpPerMsk")
    public float misakaCpPerMsk = 2.0f;

    /** Energy drained per tick by an energy laser tower while powering a normal relay satellite.
     * Hyper relays multiply by {@link #misakaRelayHyperDrainMultiplier}. While healing crash debt
     * after an outage, the tower drains an additional equal amount to recover one debt tick per tick. */
    @SerializedName("misakaRelayLaserDrainPerTick")
    public int misakaRelayLaserDrainPerTick = 2000;

    /** Multiplier applied to {@link #misakaRelayLaserDrainPerTick} for hyper relays (maintain + recovery). */
    @SerializedName("misakaRelayHyperDrainMultiplier")
    public int misakaRelayHyperDrainMultiplier = 2;

    /** Consecutive unpowered ticks before a relay satellite crashes.
     * Debt only decreases when a laser pays the recovery surcharge; restore power alone does not clear it. */
    @SerializedName("misakaRelayCrashTicks")
    public int misakaRelayCrashTicks = 6000;

    /** Explosion radius when a crashed relay satellite hits the ground (TNT-like destroy). */
    @SerializedName("misakaRelayCrashExplosionPower")
    public float misakaRelayCrashExplosionPower = 5.0f;

    /**
     * Logical orbit altitude (Advanced Rocketry–style OrbitHeight).
     * Visual Y is clamped to world top − 16.
     */
    @SerializedName("misakaRelayOrbitHeight")
    public int misakaRelayOrbitHeight = 1000;

    /** Ticks from launch pad to scheduled orbit insertion (default one game minute). */
    @SerializedName("misakaRelayLaunchTicks")
    public int misakaRelayLaunchTicks = 1200;

    /** Cooldown between ActiveNetwork switches (0 = use code default). */
    @SerializedName("misakaActiveNetworkCooldownTicks")
    public int misakaActiveNetworkCooldownTicks = 0;

    /** Legacy: treated as charge rate fallback when chargeRate field is unset. */
    @SerializedName("misakaOrbitalStrikeMskCost")
    public float misakaOrbitalStrikeMskCost = 200.0f;

    /** Max MSk drawn per second from the player's allocated Misaka compute while charging. */
    @SerializedName("misakaOrbitalStrikeChargeRateMskPerSec")
    public float misakaOrbitalStrikeChargeRateMskPerSec = 200.0f;

    /** Total MSk that must be charged before an orbital strike fires. */
    @SerializedName("misakaOrbitalStrikeChargeNeedMsk")
    public float misakaOrbitalStrikeChargeNeedMsk = 1000.0f;

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
        public com.google.gson.TypeAdapter<GenericConfig> getAdapter(com.google.gson.Gson gson) {
            var delegate = gson.getAdapter(GenericConfig.class);
            return new com.google.gson.TypeAdapter<>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, GenericConfig value)
                        throws java.io.IOException {
                    delegate.write(out, value);
                }

                @Override
                public GenericConfig read(com.google.gson.stream.JsonReader in)
                        throws java.io.IOException {
                    var element = com.google.gson.JsonParser.parseReader(in);
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
