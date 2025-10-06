package org.academy.mixin.common;

import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DimensionDataStorage.class)
public abstract class MixinDimensionDataStorage {
/*    @Inject(method = "save", at = @At("TAIL"))
    public void save(CallbackInfo ci) {
        WorldData.saveData();
        if (AcademyCraftServer.serverConfig != null) {
            AcademyCraftServer.serverConfig.save();
        } else {
            AcademyCraft.LOGGER.warn("MixinDimensionDataStorage : serverConfig is null!");
        }
    }*/
}