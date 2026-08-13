package org.academy.internal.client.render.fluid;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;

public final class ImagPhaseFluidRenderer implements CustomFluidRenderer {
    public static final ImagPhaseFluidRenderer INSTANCE = new ImagPhaseFluidRenderer();

    private ImagPhaseFluidRenderer() {
    }

    @Override
    public boolean renderFluid(
            FluidRenderer renderer,
            FluidState fluidState,
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState
    ) {
        // Let the normal translucent fluid mesh provide the black liquid body, including
        // vanilla still/flowing texture differences. Stars are real particles in the volume.
        return false;
    }
}
