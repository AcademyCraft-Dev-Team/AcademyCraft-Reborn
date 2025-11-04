package org.academy.internal.common.world.level.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public class Fluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, MODID);
 /*   public static final DeferredHolder<Fluid, ImagiphasePlasma.Flowing> FLOWING_IMAGIPHASE_PLASMA = FLUIDS.register("flowing_imagiphase_plasma", ImagiphasePlasma.Flowing::new);
    public static final DeferredHolder<Fluid, ImagiphasePlasma.Source> IMAGIPHASE_PLASMA = FLUIDS.register("imagiphase_plasma", ImagiphasePlasma.Source::new);
*/}