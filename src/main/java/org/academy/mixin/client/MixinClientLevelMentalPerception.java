package org.academy.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevelMentalPerception {
    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void academy$suppressDistortedAmbientParticle(
            ParticleOptions options,
            boolean force,
            boolean decreased,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci
    ) {
        var id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        if (id == null || isCombatParticle(id.getPath())) return;
        if (MentalIntrusionClientState.shouldSuppressAmbientAt(x, y, z, 1.5)) ci.cancel();
    }

    private static boolean isCombatParticle(String path) {
        return path.contains("damage_indicator")
                || path.contains("crit")
                || path.contains("enchanted_hit")
                || path.contains("sweep_attack");
    }
}
