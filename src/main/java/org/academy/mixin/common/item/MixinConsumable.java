package org.academy.mixin.common.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.spongepowered.asm.mixin.Mixin;

/** Marks every item consumption side effect as an action initiated by its user. */
@Mixin(Consumable.class)
public abstract class MixinConsumable {
    @WrapMethod(method = "onConsume")
    private ItemStack academy$selfSourceConsumeEffects(
            Level level,
            LivingEntity user,
            ItemStack stack,
            Operation<ItemStack> original
    ) {
        return EntityMotionGuard.callWithMotionSource(
                user,
                () -> original.call(level, user, stack)
        );
    }
}
