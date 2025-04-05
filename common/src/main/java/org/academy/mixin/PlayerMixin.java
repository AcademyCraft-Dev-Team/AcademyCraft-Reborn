package org.academy.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.builtin.accelerator.skills.VectorReflection;
import org.academy.internal.common.world.entity.player.PlayerSyncSkillData;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin implements PlayerSyncSkillData {
    @SuppressWarnings("UnusedAssignment")
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void hurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        Pair<Boolean, Float> pair = VectorReflection.Server.handleHurt((Player) (Object) this, damageSource, amount);
        if (!pair.getLeft()) {
            cir.setReturnValue(false);
        } else {
            amount = pair.getRight();
        }
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    public void defineSynchedData(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(SKILL_DATA, new CompoundTag());
    }

    @Override
    public CompoundTag academyCraft$getSkillData() {
        return ((Player) (Object) this).getEntityData().get(SKILL_DATA);
    }
}