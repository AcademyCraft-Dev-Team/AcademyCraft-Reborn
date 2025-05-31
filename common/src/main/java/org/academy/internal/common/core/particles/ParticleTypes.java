package org.academy.internal.common.core.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ParticleTypes {
    public static final Map<String, ParticleType<?>> PARTICLE_TYPES = new HashMap<>();
    public static final Map<ParticleType<?>, ParticleEngine.SpriteParticleRegistration<?>> PARTICLE_PROVIDERS = new HashMap<>();
    public static final SimpleParticleType IMAG_PHASE = register("imag_phase",
            new SimpleParticleType(true));

    static {
        PARTICLE_PROVIDERS.put(IMAG_PHASE, new ParticleEngine.SpriteParticleRegistration<>() {
            @Override
            public @NotNull ParticleProvider<ParticleOptions> create(@NotNull SpriteSet spriteSet) {
                return new ParticleProvider<>() {
                    @Override
                    public @NotNull Particle createParticle(@NotNull ParticleOptions type, @NotNull ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
                        SuspendedParticle suspendedparticle = new SuspendedParticle(level, spriteSet, x, y, z) {
                            @Override
                            public @NotNull ParticleRenderType getRenderType() {
                                return ParticleRenderTypes.IMAG_PHASE;
                            }
                        };

                        Random random = MathUtil.RANDOM;

                        suspendedparticle.scale(random.nextFloat(0.5f, 0.75f));
                        String hexColor1String = "88DEFF";
                        String hexColor2String = "9971E0";

                        int r1Int = Integer.parseInt(hexColor1String.substring(0, 2), 16);
                        int g1Int = Integer.parseInt(hexColor1String.substring(2, 4), 16);
                        int b1Int = Integer.parseInt(hexColor1String.substring(4, 6), 16);

                        float r1Float = r1Int / 255.0f;
                        float g1Float = g1Int / 255.0f;
                        float b1Float = b1Int / 255.0f;

                        int r2Int = Integer.parseInt(hexColor2String.substring(0, 2), 16);
                        int g2Int = Integer.parseInt(hexColor2String.substring(2, 4), 16);
                        int b2Int = Integer.parseInt(hexColor2String.substring(4, 6), 16);

                        float r2Float = r2Int / 255.0f;
                        float g2Float = g2Int / 255.0f;
                        float b2Float = b2Int / 255.0f;

                        float finalR;
                        float originR = Math.min(r1Float, r2Float);
                        float boundR = Math.max(r1Float, r2Float);
                        finalR = random.nextFloat(originR, boundR);

                        float finalG;
                        float originG = Math.min(g1Float, g2Float);
                        float boundG = Math.max(g1Float, g2Float);
                        finalG = random.nextFloat(originG, boundG);

                        float finalB;
                        float originB = Math.min(b1Float, b2Float);
                        float boundB = Math.max(b1Float, b2Float);
                        finalB = random.nextFloat(originB, boundB);

                        finalR = Math.max(0.0f, Math.min(1.0f, finalR));
                        finalG = Math.max(0.0f, Math.min(1.0f, finalG));
                        finalB = Math.max(0.0f, Math.min(1.0f, finalB));

                        suspendedparticle.setColor(finalR, finalG, finalB);
                        return suspendedparticle;
                    }
                };
            }
        });
    }

    public static <T extends ParticleType<?>> T register(String key, T particleType) {
        PARTICLE_TYPES.put(key, particleType);
        return particleType;
    }
}