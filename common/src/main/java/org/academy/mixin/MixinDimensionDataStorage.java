package org.academy.mixin;

import net.minecraft.world.level.storage.DimensionDataStorage;
import org.academy.internal.server.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionDataStorage.class)
public abstract class MixinDimensionDataStorage {
    @Inject(method = "save", at = @At("TAIL"))
    public void save(CallbackInfo ci) {
        WorldData.saveData();
    }
}