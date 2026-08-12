package org.academy.internal.common.world.level.material;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static org.academy.AcademyCraft.MODID;

public final class Fluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);

    public static final DeferredHolder<FluidType, FluidType> IMAG_PHASE_TYPE =
            FLUID_TYPES.register("imag_phase", () -> new FluidType(
                    FluidType.Properties.create()
                            .descriptionId("fluid.academy.imag_phase")
                            .canSwim(true)
                            .canPushEntity(true)
                            .isWaterLike(true)
            ));
    public static final DeferredHolder<Fluid, ImagPhaseFluid.Source> IMAG_PHASE =
            FLUIDS.register("imag_phase", ImagPhaseFluid.Source::new);
    public static final DeferredHolder<Fluid, ImagPhaseFluid.Flowing> FLOWING_IMAG_PHASE =
            FLUIDS.register("flowing_imag_phase", ImagPhaseFluid.Flowing::new);

    private Fluids() {
    }
}
