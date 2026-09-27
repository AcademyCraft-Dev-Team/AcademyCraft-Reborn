package org.academy.mixin.common;

import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.entitycontrol.HealthDataOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public abstract class MixinSynchedHealthOwner {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void academy$bindHealthItem(SyncedDataHolder holder, SynchedEntityData.DataItem<?>[] items, CallbackInfo ci) {
        if (!(holder instanceof LivingEntity living)) return;
        var id = LivingHealthDataAccessor.academy$healthAccessor().id();
        if (id < items.length) ((HealthDataOwner) items[id]).academy$bindHealthOwner(living);
    }
}
