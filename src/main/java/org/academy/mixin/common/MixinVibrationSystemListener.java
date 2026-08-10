package org.academy.mixin.common;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.MentalPerceptionApi;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VibrationSystem.Listener.class)
public abstract class MixinVibrationSystemListener {
    @Shadow
    @Final
    private VibrationSystem system;

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void academy$rejectWardenVibrationsDuringMentalStupor(
            ServerLevel level,
            Holder<GameEvent> event,
            GameEvent.Context context,
            Vec3 sourcePosition,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (system instanceof Warden warden) {
            if (MentalControlRuntime.isFrozen(warden)) {
                cir.setReturnValue(false);
                return;
            }
            if (context.sourceEntity() instanceof LivingEntity source
                    && !MentalPerceptionApi.canPerceive(warden, source)) {
                cir.setReturnValue(false);
            }
        }
    }
}
