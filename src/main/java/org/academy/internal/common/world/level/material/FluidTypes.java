package org.academy.internal.common.world.level.material;

import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static org.academy.AcademyCraft.MODID;

public final class FluidTypes {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MODID);
    public static final DeferredHolder<FluidType, FluidType> IMAGIPHASE_PLASMA = FLUID_TYPES.register("imagiphase_plasma", () -> new FluidType(
            FluidType.Properties.create()
                    .canSwim(false)
                    .adjacentPathType(PathType.BLOCKED)
                    .canConvertToSource(false)
                    .fallDistanceModifier(1f)
                    .canPushEntity(false)
                    .temperature(0)
    ));

    private FluidTypes() {
    }
}