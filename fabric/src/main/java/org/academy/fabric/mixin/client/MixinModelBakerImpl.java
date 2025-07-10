package org.academy.fabric.mixin.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.renderer.model.CoinModelGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ModelBakery.ModelBakerImpl.class)
public abstract class MixinModelBakerImpl {
    @Shadow
    public abstract UnbakedModel getModel(ResourceLocation p_248568_);

    @Shadow
    @Final
    private Function<Material, TextureAtlasSprite> modelTextureGetter;

    @Inject(method = "bake", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBakery$ModelBakerImpl;getModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;"), cancellable = true)
    private void bake(ResourceLocation location, ModelState transform, CallbackInfoReturnable<BakedModel> cir) {
        if (getModel(location) instanceof BlockModel blockModel) {
            if (blockModel.getRootModel() == CoinModelGenerator.COIN) {
                cir.setReturnValue(CoinModelGenerator.INSTANCE.generateBlockModel(modelTextureGetter, blockModel).bake((ModelBaker) this, blockModel, modelTextureGetter, transform, location, false));
            }
        }
    }
}