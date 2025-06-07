package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LightTexture;

public class ArcParticle extends SuspendedParticle {
    public ArcParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, sprites, x, y, z);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}