package org.academy.mixin.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import org.academy.internal.common.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ItemBlockRenderTypes.class)
public abstract class MixinItemBlockRenderTypes {
    @Shadow @Final private static Map<Block, RenderType> TYPE_BY_BLOCK;

    @Inject(method = "<clinit>",at = @At("TAIL"))
    private static void onClinit(CallbackInfo ci) {
        TYPE_BY_BLOCK.put(Blocks.IMAGIPHASE_LICHEN.get(), RenderType.cutout());
    }
}