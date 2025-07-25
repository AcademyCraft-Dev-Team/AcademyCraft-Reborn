package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.NotNull;

public class MediumArcParticle extends FixedTextureSheetParticle {
    private final SpriteSet sprites;

    protected MediumArcParticle(ClientLevel level, double x, double y, double z, float newYaw, float newPitch, SpriteSet newSpriteSet) {
        super(level, x, y, z, newYaw, newPitch);
        sprites = newSpriteSet;
        pickSprite(sprites);
        hasPhysics = false;
        quadSize *= 10;
    }

    @Override
    public void tick() {
        super.tick();
        setSprite(sprites.get(age % 8, 7));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}