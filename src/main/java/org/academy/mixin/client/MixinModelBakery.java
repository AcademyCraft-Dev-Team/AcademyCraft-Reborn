package org.academy.mixin.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.model.CoinModelGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBakery.class)
public abstract class MixinModelBakery {
    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void loadBlockModel(ResourceLocation location, CallbackInfoReturnable<BlockModel> cir) {
        if (location.getNamespace().equals(AcademyCraft.MODID)) {
            if (location.getPath().equals("builtin/coin")) {
                cir.setReturnValue(CoinModelGenerator.COIN);
            }
        }
    }
}