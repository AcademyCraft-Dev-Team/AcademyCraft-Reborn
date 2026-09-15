package org.academy.mixin.common;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingHealthDataAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> academy$healthAccessor() { throw new AssertionError(); }
}
