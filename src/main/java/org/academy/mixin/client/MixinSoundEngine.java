package org.academy.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import org.academy.internal.client.app.mediaplayer.MediaPlayerBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For MediaPlayerBackend
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Inject(method = "reload", at = @At("TAIL"))
    public void reload(CallbackInfo ci) {
        MediaPlayerBackend.handleContextReset();
    }
}