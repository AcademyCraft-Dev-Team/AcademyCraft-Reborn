package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class MixinExperienceOrbNativeStructureDrops {
    @Inject(method = "awardWithDirection", at = @At("HEAD"), cancellable = true)
    private static void academy$captureNativeMiningExperience(ServerLevel level, Vec3 position, Vec3 direction,
                                                               int amount, CallbackInfo callback) {
        if (NativeStructureBreakDrops.tryCaptureExperience(level, position, direction, amount)) callback.cancel();
    }
}
