package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.NotNull;

public class ImagiphaseFluidParticle extends SuspendedParticle {
    public ImagiphaseFluidParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, sprites, x, y, z);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderTypes.IMAG_PHASE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}