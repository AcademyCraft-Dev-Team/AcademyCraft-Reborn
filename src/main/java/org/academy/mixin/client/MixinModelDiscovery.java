package org.academy.mixin.client;

import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import org.academy.api.client.Resource;
import org.academy.api.client.resources.model.cuboid.CoinModelGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ModelDiscovery.class)
public abstract class MixinModelDiscovery {
    @Shadow
    public abstract void addSpecialModel(Identifier id, UnbakedModel model);

    /**
     * 用于添加 CoinModelGenerator 喵, 没 API 只能 mixin 了喵
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void coin(Map<Identifier, UnbakedModel> unbakedModels, UnbakedModel missingUnbakedModel, CallbackInfo ci) {
        addSpecialModel(Resource.Models.COIN_ITEM_MODEL_ID, CoinModelGenerator.INSTANCE);
    }
}