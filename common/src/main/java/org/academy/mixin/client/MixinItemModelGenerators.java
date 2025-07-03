package org.academy.mixin.client;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.renderer.model.ModelTemplates;
import org.academy.internal.common.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Mixin(ItemModelGenerators.class)
public abstract class MixinItemModelGenerators {
    @Shadow
    @Final
    private BiConsumer<ResourceLocation, Supplier<JsonElement>> output;

    @Inject(method = "run", at = @At("TAIL"))
    private void runMixin(CallbackInfo ci) {
        var itemKey = BuiltInRegistries.ITEM.getKey(Items.COIN);
        ModelTemplates.COIN.create(ModelLocationUtils.getModelLocation(Items.COIN), new TextureMapping().put(TextureSlot.FRONT, itemKey).put(TextureSlot.BACK, itemKey), output);
    }
}