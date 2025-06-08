package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LightTexture;

public class ArcParticle extends SuspendedParticle {
    private final SpriteSet sprites;

    public ArcParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, sprites, x, y, z);
        this.sprites = sprites;
        quadSize *= 2;
        lifetime /= 4;
    }

    @Override
    public void tick() {
        super.tick();
        setSprite(sprites.get(age % 4, 3));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }
}