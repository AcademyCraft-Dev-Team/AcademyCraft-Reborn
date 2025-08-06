package org.academy.mixin.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import org.academy.api.client.renderer.model.CoinModelGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ModelBakery.ModelBakerImpl.class)
public abstract class MixinModelBakerImpl {
    /**
     * For Coin Model
     */
    @Inject(method = "bakeUncached(Lnet/minecraft/client/resources/model/UnbakedModel;Lnet/minecraft/client/resources/model/ModelState;Ljava/util/function/Function;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At(value = "HEAD"), cancellable = true)
    private void bake(UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> sprites, CallbackInfoReturnable<BakedModel> cir) {
        if (model instanceof BlockModel blockmodel && blockmodel.getRootModel() == CoinModelGenerator.COIN) {
            cir.setReturnValue(CoinModelGenerator.INSTANCE
                    .generateBlockModel(sprites, blockmodel)
                    .bake((ModelBaker) this, blockmodel, sprites, state, false));
        }
    }
}