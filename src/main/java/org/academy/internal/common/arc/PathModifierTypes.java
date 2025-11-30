package org.academy.internal.common.arc;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.modifier.*;
import org.academy.api.common.registries.Registries;

public final class PathModifierTypes {
    public static final DeferredRegister<PathModifierType<?>> PATH_MODIFIER_TYPES =
            DeferredRegister.create(Registries.Keys.PATH_MODIFIER_TYPES, AcademyCraft.MOD_ID);
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<DisplacementModifier>> DISPLACEMENT =
            PATH_MODIFIER_TYPES.register("displacement", () -> new PathModifierType<>(DisplacementModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<HelixModifier>> HELIX =
            PATH_MODIFIER_TYPES.register("helix", () -> new PathModifierType<>(HelixModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<TaperModifier>> TAPER =
            PATH_MODIFIER_TYPES.register("taper", () -> new PathModifierType<>(TaperModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<ColorModifier>> COLOR =
            PATH_MODIFIER_TYPES.register("color", () -> new PathModifierType<>(ColorModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<NoiseFieldModifier>> NOISE_FIELD =
            PATH_MODIFIER_TYPES.register("noise_field", () -> new PathModifierType<>(NoiseFieldModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<TargetSeekModifier>> TARGET_SEEK =
            PATH_MODIFIER_TYPES.register("target_seek", () -> new PathModifierType<>(TargetSeekModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<JaggedModifier>> JAGGED =
            PATH_MODIFIER_TYPES.register("jagged", () -> new PathModifierType<>(JaggedModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<StretchModifier>> STRETCH =
            PATH_MODIFIER_TYPES.register("stretch", () -> new PathModifierType<>(StretchModifier.CODEC));
    public static final DeferredHolder<PathModifierType<?>, PathModifierType<WarpModifier>> WARP =
            PATH_MODIFIER_TYPES.register("warp", () -> new PathModifierType<>(WarpModifier.CODEC));

    private PathModifierTypes() {
    }
}