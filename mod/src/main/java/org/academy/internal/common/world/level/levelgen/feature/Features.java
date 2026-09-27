package org.academy.internal.common.world.level.levelgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MOD_ID;

public final class Features {
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, MOD_ID);
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<ImagPhaseLakeFeature>> IMAG_PHASE_LAKE =
            FEATURES.register("imag_phase_lake", () -> ImagPhaseLakeFeature.CODEC);

    private Features() {
    }
}
