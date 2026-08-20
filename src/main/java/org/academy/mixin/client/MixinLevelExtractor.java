package org.academy.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void academy$keepWingFlyerVisibleWhileChunksCompile(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MentalIntrusionClientState.isHidden(entity)) {
            cir.setReturnValue(false);
            return;
        }
        // Vanilla additionally requires the entity's chunk section to be compiled and
        // visible. A fast player can outrun that compilation and blink every other frame.
        if (entity instanceof Avatar avatar && WingFlightPose.hasActiveFlightPose(avatar)) {
            cir.setReturnValue(true);
        }
    }
}
