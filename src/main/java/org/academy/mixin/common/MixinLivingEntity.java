package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
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
        var self = (LivingEntity) (Object) this;
        if (self.level() instanceof ServerLevel level) {
            var data = self.getData(AttachmentTypes.QUANTUM_DATA.get());

            if (data.active()) {
                var maxHealth = self.getMaxHealth();
                var currentHealth = self.getHealth();
                var newDuration = data.duration() - 1;

                if (newDuration <= 0) {
                    QuantumUtil.disableQuantum(self);
                    return;
                } else {
                    self.setData(AttachmentTypes.QUANTUM_DATA.get(),
                            new QuantumData(true, data.intensity(), data.color(), newDuration));
                }

                if (self.tickCount % 20 == 0) {
                    var randomFactor = self.getRandom().nextFloat();
                    var bias = 0.65f;
                    var amplitude = maxHealth * 0.05f;
                    if (amplitude < 1.0f) amplitude = 1.0f;

                    var change = (randomFactor - bias) * amplitude;
                    change -= 0.2f;

                    var nextHealth = currentHealth + change;
                    if (nextHealth <= 0.0f) {
                        self.invulnerableTime = 0;
                        self.hurtServer(level, self.damageSources().generic(), Float.MAX_VALUE);
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
}