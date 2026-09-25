package org.academy.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.app.music.backend.MusicPlayerBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For MediaPlayerBackend
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void academy$suppressDistortedFootsteps(
            SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir
    ) {
        var path = sound.getIdentifier().getPath();
        if ((path.contains("step") || path.contains("footstep"))
                && MentalIntrusionClientState.shouldSuppressAmbientAt(
                sound.getX(), sound.getY(), sound.getZ(), 1.5)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    @Inject(method = "reload", at = @At("TAIL"))
    public void reload(CallbackInfo ci) {
        MusicPlayerBackend.Companion.getInstance().handleContextReset();
    }
}
