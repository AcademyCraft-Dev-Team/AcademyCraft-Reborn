package org.academy.internal.common.world.level.levelgen.feature;

import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Features {
    public static final Map<String, Feature<?>> FEATURES = new HashMap<>();
    public static final Feature<ImagPhaseLakeFeature.Configuration> IMAG_PHASE_LAKE = register("imag_phase_lake", new ImagPhaseLakeFeature(ImagPhaseLakeFeature.Configuration.CODEC));

    public static <T extends Feature<?>> T register(String key, T feature) {
        FEATURES.put(key, feature);
        return feature;
    }

    private Features() {
    }
}