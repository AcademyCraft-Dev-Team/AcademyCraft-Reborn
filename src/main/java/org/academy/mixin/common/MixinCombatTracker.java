package org.academy.mixin.common;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CombatTracker.class)
public abstract class MixinCombatTracker {
    @Shadow @Final private LivingEntity mob;

    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void academy$overrideQuantumDeathMessage(CallbackInfoReturnable<Component> cir) {
        var data = mob.getData(AttachmentTypes.QUANTUM_DATA.get());
        if (data.active()) {
            Component message = Component.translatable(
                    "death.attack.academy.quantum_collapse",
                    mob.getDisplayName()
            );
            cir.setReturnValue(message);
        }
    }
}