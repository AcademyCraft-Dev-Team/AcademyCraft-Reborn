package org.academy.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MixinMeltdownerResistance {
    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean academy$bypassResistance(DamageSource source, TagKey<DamageType> tag,
                                              Operation<Boolean> original) {
        return tag == DamageTypeTags.BYPASSES_RESISTANCE && source.is(DamageTypes.MELT_DAMAGE)
                || original.call(source, tag);
    }
}
