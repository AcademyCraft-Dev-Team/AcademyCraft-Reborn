package org.academy.mixin.common;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float academy$amplifyQuantumDamage(float amount, DamageSource source) {
        if (amount <= 0) return amount;
        if ((Object) this instanceof LivingEntity self) {
            if (self.level().isClientSide()) return amount;
            QuantumData data = self.getData(AttachmentTypes.QUANTUM_DATA.get());

            //量子易伤：+15%
            if (data.active()) {
                return amount * 1.15f;
            }
        }

        return amount;
    }
}