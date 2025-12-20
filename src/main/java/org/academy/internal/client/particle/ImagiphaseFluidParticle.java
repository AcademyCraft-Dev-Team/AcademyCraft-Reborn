/*
package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.academy.api.client.Render;

public class ImagiphaseFluidParticle extends SuspendedParticle {
    private final SpriteSet sprites;
    public static final SingleQuadParticle.Layer LAYER = new SingleQuadParticle.Layer(
            false, TextureAtlas.LOCATION_PARTICLES, Render.RenderPipelines.NO_DEPTH_OPAQUE_PARTICLE
    );

    public ImagiphaseFluidParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }


    @Override
    public Layer getLayer() {
        return LAYER;
    }
}*/
