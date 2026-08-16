package org.academy.internal.common.world.level.levelgen.feature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public final class Features {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MODID);
    public static final DeferredHolder<Feature<?>, Feature<ImagPhaseLakeFeature.Configuration>> IMAG_PHASE_LAKE =
            FEATURES.register(
                    "imag_phase_lake",
                    () -> new ImagPhaseLakeFeature(ImagPhaseLakeFeature.Configuration.CODEC)
            );

    private Features() {
    }
}
