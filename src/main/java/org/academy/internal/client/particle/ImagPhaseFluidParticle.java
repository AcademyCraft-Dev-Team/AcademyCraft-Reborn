package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.util.RandomSource;

public final class ImagPhaseFluidParticle extends SuspendedParticle {
    public ImagPhaseFluidParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z, RandomSource random) {
        super(level, x, y, z, sprites.get(random));
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }
}
