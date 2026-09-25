package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.academy.api.server.world.WaterSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class MixinFlowingFluidWaterSuppression {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void academy$suppressWater(LevelAccessor level, BlockPos pos, BlockState state,
                                       Direction direction, FluidState fluid, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && fluid.is(FluidTags.WATER)
                && WaterSuppression.contains(serverLevel, pos)) ci.cancel();
    }
}
