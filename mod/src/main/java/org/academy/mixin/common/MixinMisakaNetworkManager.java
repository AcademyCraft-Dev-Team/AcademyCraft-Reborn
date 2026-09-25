package org.academy.mixin.common;

import org.academy.internal.common.network.NetworkRegistrationPolicy;
import org.misaka.api.common.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkManager.class, remap = false)
public abstract class MixinMisakaNetworkManager {
    @Inject(method = "register(Ljava/lang/Class;)V", at = @At("HEAD"), remap = false)
    private void academy$replaceStaticRegistration(Class<?> listenerClass, CallbackInfo ci) {
        NetworkRegistrationPolicy.replaceStaticRegistration(
                (NetworkManager) (Object) this,
                listenerClass
        );
    }
}
