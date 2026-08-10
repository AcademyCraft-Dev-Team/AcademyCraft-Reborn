package org.academy.internal.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

public final class BloodSprayParticle extends SingleQuadParticle {
    private static final int MIN_LIFETIME = 50;
    private static final int RANDOM_LIFETIME = 21;
    private static final int FADE_TICKS = 20;
    private static final float INITIAL_ALPHA = 0.78f;

    private final Quaternionf surfaceRotation;

    private BloodSprayParticle(ClientLevel level, double x, double y, double z,
                               double normalX, double normalY, double normalZ,
                               SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(random));
        surfaceRotation = surfaceRotation(normalX, normalY, normalZ);
        lifetime = MIN_LIFETIME + random.nextInt(RANDOM_LIFETIME);
        quadSize = 0.8f + random.nextFloat() * 0.6f;
        hasPhysics = false;
        rCol = 0.82f;
        gCol = 0.03f;
        bCol = 0.03f;
        alpha = INITIAL_ALPHA;
        roll = random.nextFloat() * (float) (Math.PI * 2.0);
        oRoll = roll;
    }

    private static Quaternionf surfaceRotation(double normalX, double normalY, double normalZ) {
        if (Math.abs(normalY) > 0.5) {
            return new Quaternionf().rotateX(normalY > 0.0 ? -Mth.HALF_PI : Mth.HALF_PI);
        }
        if (Math.abs(normalX) > 0.5) {
            return new Quaternionf().rotateY(normalX > 0.0 ? Mth.HALF_PI : -Mth.HALF_PI);
        }
        if (normalZ < -0.5) {
            return new Quaternionf().rotateY(Mth.PI);
        }
        return new Quaternionf();
    }

    @Override
    public void extract(QuadParticleRenderState renderState, Camera camera, float partialTick) {
        var rotation = new Quaternionf(surfaceRotation);
        if (roll != 0.0f) {
            rotation.rotateZ(Mth.lerp(partialTick, oRoll, roll));
        }
        extractRotatedQuad(renderState, camera, rotation, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - FADE_TICKS) {
            alpha = INITIAL_ALPHA * Math.max(0.0f, (float) (lifetime - age) / FADE_TICKS);
        }
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
            return new BloodSprayParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, random
            );
        }
    }
}
