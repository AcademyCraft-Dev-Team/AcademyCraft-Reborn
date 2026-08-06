package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
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

    @Accessor("damageContainers")
    Stack<DamageContainer> academy$getDamageContainers();
}
