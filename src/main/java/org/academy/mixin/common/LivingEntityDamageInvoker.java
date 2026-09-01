package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Stack;

@Mixin(LivingEntity.class)
public interface LivingEntityDamageInvoker {
    @Invoker("actuallyHurt")
    void academy$actuallyHurt(ServerLevel level, DamageSource source, float amount);

    @Invoker("checkTotemDeathProtection")
    boolean academy$checkTotemDeathProtection(DamageSource source);

    @Invoker("playHurtSound")
    void academy$playHurtSound(DamageSource source);

    @Invoker("playSecondaryHurtSound")
    void academy$playSecondaryHurtSound(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent academy$getDeathSound();

    @Invoker("getSoundVolume")
    float academy$getSoundVolume();

    @Invoker("handleKillingBlow")
    void academy$handleKillingBlow();

    @Invoker("dropAllDeathLoot")
    void academy$dropAllDeathLoot(ServerLevel level, DamageSource source);

    @Accessor("damageContainers")
    Stack<DamageContainer> academy$getDamageContainers();

    @Accessor("dead")
    boolean academy$isDead();

    @Accessor("lastHurt")
    void academy$setLastHurt(float amount);

    @Accessor("lastDamageSource")
    void academy$setLastDamageSource(DamageSource source);

    @Accessor("lastDamageStamp")
    void academy$setLastDamageStamp(long gameTime);
}
