package org.academy.internal.common.core.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;

import java.util.HashMap;
import java.util.Map;

public class ParticleTypes {
    public static final Map<String, ParticleType<?>> PARTICLE_TYPES = new HashMap<>();
    public static final SimpleParticleType IMAG_PHASE_FLUID = register("imag_phase_fluid",
            new SimpleParticleType(true));
    public static final SimpleParticleType IMAG_PHASE_LEAVES = register("imag_phase_leaves",
            new SimpleParticleType(false));

    public static <T extends ParticleType<?>> T register(String key, T particleType) {
        PARTICLE_TYPES.put(key, particleType);
        return particleType;
    }
}