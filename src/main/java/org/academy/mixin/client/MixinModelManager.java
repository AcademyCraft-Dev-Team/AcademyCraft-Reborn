package org.academy.mixin.client;

import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.Zone;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader;
import org.academy.api.client.Resource;
import org.academy.api.client.renderer.model.CoinModelGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;

@Mixin(ModelManager.class)
public abstract class MixinModelManager {
    /**
     * Maybe Neo should add an Event ?
     */
    @Inject(method = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;Lnet/neoforged/neoforge/client/model/standalone/StandaloneModelLoader$LoadedModels;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelDiscovery;addSpecialModel(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/resources/model/UnbakedModel;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void coin(Map<ResourceLocation, UnbakedModel> inputModels, BlockStateModelLoader.LoadedModels loadedModels, ClientItemInfoLoader.LoadedClientInfos loadedClientInfos, StandaloneModelLoader.LoadedModels standaloneModels, CallbackInfoReturnable<ModelManager.ResolvedModels> cir, Zone zone, ModelDiscovery modeldiscovery) {
        modeldiscovery.addSpecialModel(Resource.Models.COIN_ITEM_MODEL_ID, CoinModelGenerator.INSTANCE);
    }
}