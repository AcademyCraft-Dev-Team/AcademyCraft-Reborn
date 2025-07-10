package org.academy.internal.common.world.level.material;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

public class Fluids {
    public static final Map<String, Fluid> FLUIDS = new HashMap<>();
    public static final FlowingFluid FLOWING_IMAGIPHASE_PLASMA = register("flowing_imagiphase_plasma", new ImagiphasePlasma.Flowing());
    public static final FlowingFluid IMAGIPHASE_PLASMA = register("imagiphase_plasma", new ImagiphasePlasma.Source());

    public static <T extends Fluid> T register(String key, T fluid) {
        FLUIDS.put(key, fluid);
        return fluid;
    }
}