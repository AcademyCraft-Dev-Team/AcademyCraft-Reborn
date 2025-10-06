package org.academy.mixin.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemBlockRenderTypes.class)
public abstract class MixinItemBlockRenderTypes {
/*    @Shadow @Final private static Map<Block, RenderType> TYPE_BY_BLOCK;

    @Inject(method = "<clinit>",at = @At("TAIL"))
    private static void onClinit(CallbackInfo ci) {
        TYPE_BY_BLOCK.put(Blocks.IMAGIPHASE_LICHEN.get(), RenderType.cutout());
    }*/
}