package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAttackStateAccessor {
    @Accessor("attackStrengthTicker")
    int academy$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void academy$setAttackStrengthTicker(int value);

    @Accessor("itemSwapTicker")
    int academy$getItemSwapTicker();

    @Accessor("itemSwapTicker")
    void academy$setItemSwapTicker(int value);
}
