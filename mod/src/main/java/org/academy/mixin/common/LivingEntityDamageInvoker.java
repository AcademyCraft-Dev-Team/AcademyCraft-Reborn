package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityDamageInvoker {
    @Invoker("actuallyHurt")
    void academy$actuallyHurt(ServerLevel level, DamageSource source, float amount);

    @Invoker("playHurtSound")
    void academy$playHurtSound(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent academy$getDeathSound();

    @Invoker("getSoundVolume")
    float academy$getSoundVolume();

    @Invoker("handleKillingBlow")
    void academy$handleKillingBlow();

    @Invoker("dropAllDeathLoot")
    void academy$dropAllDeathLoot(ServerLevel level, DamageSource source);
}
