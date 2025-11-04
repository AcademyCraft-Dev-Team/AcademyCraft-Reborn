package org.academy.internal.common.world.level.levelgen.feature;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

@SuppressWarnings("unused")
public final class Features {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, MODID);
/*
    public static final DeferredHolder<Feature<?>, ImagiphaseLakeFeature> IMAG_PHASE_LAKE = FEATURES.register("imag_phase_lake", () -> new ImagiphaseLakeFeature(ImagiphaseLakeFeature.Configuration.CODEC));
*/

    private Features() {
    }
}