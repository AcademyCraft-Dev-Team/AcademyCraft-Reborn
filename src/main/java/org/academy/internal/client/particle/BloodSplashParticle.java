package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;

public final class BloodSplashParticle extends SingleQuadParticle {
    private static final int SPLASH_LIFETIME = 6;
    private static final double SPEED_MULTIPLIER = 3.5;

    private final SpriteSet sprites;

    private BloodSplashParticle(ClientLevel level, double x, double y, double z,
                                double xSpeed, double ySpeed, double zSpeed,
                                SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites.first());
        this.sprites = sprites;
        setParticleSpeed(
                xSpeed * SPEED_MULTIPLIER,
                ySpeed * SPEED_MULTIPLIER,
                zSpeed * SPEED_MULTIPLIER
        );
        lifetime = SPLASH_LIFETIME;
        quadSize = 0.8f + random.nextFloat() * 0.5f;
        gravity = 0.1f;
        friction = 0.82f;
        hasPhysics = false;
        rCol = 0.9f;
        gCol = 0.05f;
        bCol = 0.05f;
        alpha = 0.9f;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new BloodSplashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, random);
        }
    }
}
