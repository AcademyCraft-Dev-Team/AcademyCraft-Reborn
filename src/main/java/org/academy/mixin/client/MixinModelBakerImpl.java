package org.academy.mixin.client;

import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelBakery.ModelBakerImpl.class)
public abstract class MixinModelBakerImpl {
}