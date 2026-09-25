package org.academy.internal.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class ImagPhaseLeavesParticle extends SingleQuadParticle {
    private static final int HANGING_TICKS = 40;
    private static final int FALLING_TICKS = 40;

    private final SpriteSet sprites;

    private ImagPhaseLeavesParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites,
            RandomSource random
    ) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.sprites = sprites;
        setSize(0.1F, 0.1F);
        quadSize = 0.3F;
        lifetime = HANGING_TICKS + FALLING_TICKS;
        friction = 0.98F;
        gravity = 0.0F;
        roll = random.nextFloat() * (float) (Math.PI * 2.0);
        oRoll = roll;
        setSprite(sprites.get(0, 6));
    }

    @Override
    public void tick() {
        if (age == HANGING_TICKS) {
            gravity = 0.04F + random.nextFloat() * 0.36F;
        }
        super.tick();
        if (!removed && age >= HANGING_TICKS) {
            setSprite(sprites.get((age - HANGING_TICKS) % 7, 6));
        }
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
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
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new ImagPhaseLeavesParticle(level, x, y, z, sprites, random);
        }
    }
}
