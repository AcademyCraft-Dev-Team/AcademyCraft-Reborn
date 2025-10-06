package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LightTexture;

public class SmallArcParticle extends SuspendedParticle {
    private final SpriteSet sprites;

    public SmallArcParticle(ClientLevel level, SpriteSet newSprites, double x, double y, double z) {
        super(level, x, y, z, newSprites.first());
        sprites = newSprites;
        quadSize *= 2;
        lifetime /= 4;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}