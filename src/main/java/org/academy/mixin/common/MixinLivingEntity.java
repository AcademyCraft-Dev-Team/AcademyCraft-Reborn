package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.util.QuantumUtil;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$quantumHealthFluctuation(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.isDeadOrDying() || self.level().isClientSide()) {
            return;
        }


        QuantumData data = self.getData(AttachmentTypes.QUANTUM_DATA.get());

        if (data.active()) {
            float maxHealth = self.getMaxHealth();
            float currentHealth = self.getHealth();
            int newDuration = data.duration() - 1;

            if (newDuration <= 0) {
                QuantumUtil.disableQuantum(self);
                return;
            } else {
                self.setData(AttachmentTypes.QUANTUM_DATA.get(),
                        new QuantumData(true, data.intensity(), data.color(), newDuration));
            }

            if (self.tickCount % 20 == 0) {
                float randomFactor = self.getRandom().nextFloat();
                float bias = 0.65f;
                float amplitude = maxHealth * 0.05f;
                if (amplitude < 1.0f) amplitude = 1.0f;

                float change = (randomFactor - bias) * amplitude;
                change -= 0.2f;

                float nextHealth = currentHealth + change;
                if (nextHealth <= 0.0f) {
                    self.invulnerableTime = 0;
                    self.hurt(self.damageSources().generic(), Float.MAX_VALUE);
                } else {
                    if (nextHealth > maxHealth) {
                        nextHealth = maxHealth;
                    }
                    self.setHealth(nextHealth);
                }
            }
        }
    }
}