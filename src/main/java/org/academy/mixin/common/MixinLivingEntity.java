package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.util.QuantumUtil;
import org.academy.api.common.damage.SkillDamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$quantumHealthFluctuation(CallbackInfo ci) {
        QuantumUtil.quantumHealthFluctuation((LivingEntity) (Object) this);
    }

    @Inject(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V")
    )
    private void academy$onSkillHurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source instanceof SkillDamageSource skillSource) {
            if (skillSource.getEntity() instanceof ServerPlayer player) {
                skillSource.getSkill().onHurt(player, (LivingEntity) (Object) this, amount);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void academy$onSkillKill(DamageSource source, CallbackInfo ci) {
        if (source instanceof SkillDamageSource skillSource) {
            if (skillSource.getEntity() instanceof ServerPlayer player) {
                skillSource.getSkill().onKill(player, (LivingEntity) (Object) this);
            }
        }
    }
}