package org.academy.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {
    @Shadow
    protected abstract <T extends ParticleOptions> void register(ParticleType<T> particleType, ParticleEngine.SpriteParticleRegistration<T> particleMetaFactory);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "registerProviders", at = @At("TAIL"))
    private void registerProviders(CallbackInfo ci) {
        for (ParticleType<?> particleType : ParticleTypes.PARTICLE_PROVIDERS.keySet()) {
            ParticleEngine.SpriteParticleRegistration particleMetaFactory = ParticleTypes.PARTICLE_PROVIDERS.get(particleType);
            register(particleType, particleMetaFactory);
        }
    }
}