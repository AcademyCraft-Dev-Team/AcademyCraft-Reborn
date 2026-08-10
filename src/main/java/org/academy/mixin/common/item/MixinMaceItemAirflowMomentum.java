package org.academy.mixin.common.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.academy.internal.common.ability.aeromanip.skills.AirflowJet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MaceItem.class)
public abstract class MixinMaceItemAirflowMomentum {
    @Redirect(
            method = "getKnockbackPower",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;fallDistance:D"
            )
    )
    private static double academy$useAirflowMomentumForKnockback(Entity attacker) {
        return AirflowJet.Server.getEffectiveMaceFallDistance(attacker);
    }

    @Redirect(
            method = "canSmashAttack",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;fallDistance:D"
            )
    )
    private static double academy$useAirflowMomentumForSmashCheck(LivingEntity entity) {
        return AirflowJet.Server.getEffectiveMaceFallDistance(entity);
    }

    @Redirect(
            method = "hurtEnemy",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;fallDistance:D"
            )
    )
    private double academy$useAirflowMomentumForSmashSound(LivingEntity attacker) {
        return AirflowJet.Server.getEffectiveMaceFallDistance(attacker);
    }

    @Redirect(
            method = "getAttackDamageBonus",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;fallDistance:D"
            )
    )
    private double academy$useAirflowMomentumForDamage(LivingEntity entity) {
        return AirflowJet.Server.getEffectiveMaceFallDistance(entity);
    }

    @Inject(method = "postHurtEnemy", at = @At("TAIL"))
    private void academy$consumeAirflowMomentum(
            ItemStack stack,
            LivingEntity target,
            LivingEntity attacker,
            CallbackInfo ci
    ) {
        if (attacker instanceof ServerPlayer player
                && AirflowJet.Server.getEffectiveMaceFallDistance(player)
                > MaceItem.SMASH_ATTACK_FALL_THRESHOLD) {
            AirflowJet.Server.consumeMaceMomentum(player);
        }
    }
}
