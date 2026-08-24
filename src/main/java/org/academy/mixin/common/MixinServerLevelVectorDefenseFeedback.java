package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorDefenseFeedbackTickets;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevelVectorDefenseFeedback {
    @Inject(method = "broadcastDamageEvent", at = @At("HEAD"), cancellable = true)
    private void academy$suppressConfirmedVectorDamage(
            Entity entity,
            DamageSource source,
            CallbackInfo ci
    ) {
        if (entity instanceof ServerPlayer player
                && (VectorReflection.Server.usesFullInstanceProtection(player)
                || VectorDefenseFeedbackTickets.shouldSuppressDamage(player, source))) {
            ci.cancel();
        }
    }

    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
    private void academy$suppressConfirmedVectorHurtState(Entity entity, byte state, CallbackInfo ci) {
        if ((state == 2 || state == 3)
                && entity instanceof ServerPlayer player
                && (VectorReflection.Server.usesFullInstanceProtection(player)
                || VectorDefenseFeedbackTickets.shouldSuppressEntityEvent(player))) {
            ci.cancel();
        }
    }
}
