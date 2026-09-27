package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class VectorBlastParticle extends SingleQuadParticle {
    private static final float BASE_ALPHA = 0.35f;
    private final SpriteSet sprites;

    private VectorBlastParticle(ClientLevel level, double x, double y, double z,
                                double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites.first());
        this.sprites = sprites;
        lifetime = 16;
        quadSize = 1.5f;
        setSpriteFromAge(sprites);
        rCol = 1.0f;
        gCol = 1.0f;
        bCol = 1.0f;
        alpha = BASE_ALPHA;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        quadSize += 0.15f;
        if (age > lifetime / 2) {
            alpha = BASE_ALPHA * (1.0f - (age - lifetime / 2.0f) / (lifetime / 2.0f));
        }
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0x00F000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new VectorBlastParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
