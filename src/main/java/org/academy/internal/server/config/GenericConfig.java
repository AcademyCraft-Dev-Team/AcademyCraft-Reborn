package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericConfig {
    public static final String KEY = "generic";

    @SerializedName("booleanMap")
    public final Map<String, Boolean> booleanMap = new HashMap<>();

    @SerializedName("stringListMap")
    public final Map<String, List<String>> stringListMap = new HashMap<>();

    /** CP gained per 1 MSk of Misaka compute (1 MSk : 2 CP by default). */
    @SerializedName("misakaCpPerMsk")
    public float misakaCpPerMsk = 2.0f;

    /** Energy drained per tick by an energy laser tower while powering a normal relay satellite.
     * Hyper relays use twice this amount. While healing crash debt after an outage, the tower
     * drains an additional equal amount to recover one debt tick per tick. */
    @SerializedName("misakaRelayLaserDrainPerTick")
    public int misakaRelayLaserDrainPerTick = 2000;

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

    public static final class Action implements TypeHandler<GenericConfig> {
        public static final TypeHandler<GenericConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public GenericConfig getDefault() {
            var defaultConfig = new GenericConfig();
            defaultConfig.booleanMap.put("attackPlayer", true);
            defaultConfig.booleanMap.put("destroyBlocks", true);
            defaultConfig.booleanMap.put("genOres", true);
            defaultConfig.booleanMap.put("genPhaseLiquid", true);
            defaultConfig.booleanMap.put("devMode", false);
            defaultConfig.stringListMap.put(
                    "ctaFriendlyFireWhitelist",
                    List.of("tamed", "touhou_little_maid:maid")
            );
            return defaultConfig;
        }

        @Override
        public Class<GenericConfig> getTypeClass() {
            return GenericConfig.class;
        }
    }
}
