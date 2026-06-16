package org.academy.mixin.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, name = "damage")
    private float academy$amplifyQuantumDamage(float damage, DamageSource source) {
        if (damage <= 0) return damage;
        if ((Object) this instanceof LivingEntity self) {
            if (self.level().isClientSide()) return damage;
            var data = self.getData(AttachmentTypes.QUANTUM_DATA.get());

            //量子易伤：+15%
            if (data.active()) {
                return damage * 1.15f;
            }
        }

        return damage;
    }
}