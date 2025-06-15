package org.academy.mixin.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorReflection;
import org.academy.internal.common.world.entity.player.PlayerSyncData;
import org.academy.internal.common.world.item.DataTerminalItem;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer implements PlayerSyncData {
    @SuppressWarnings("UnusedAssignment")
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), cancellable = true)
    public void hurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        Pair<Boolean, Float> pair = VectorReflection.Server.onPlayerHurt((Player) (Object) this, damageSource, amount);
        if (!pair.getLeft()) {
            cir.setReturnValue(false);
        } else {
            amount = pair.getRight();
        }
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    public void defineSynchedData(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(DATA, new CompoundTag());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void readAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        compoundTag.put(DataTerminalItem.HAS_DATA_TERMINAL, ((Player) (Object) this).getEntityData().get(DATA));
    }

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void addAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        CompoundTag oldTag = player.getEntityData().get(DATA);
        CompoundTag newTag = new CompoundTag();
        oldTag.getAllKeys().forEach(key -> newTag.put(key, oldTag.get(key)));
        player.getEntityData().set(DATA, oldTag);
    }
}