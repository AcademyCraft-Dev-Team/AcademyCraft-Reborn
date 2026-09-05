package org.academy.internal.common.core.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MOD_ID;

public class ParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMAG_PHASE_FLUID = PARTICLE_TYPES.register("imag_phase_fluid",
            () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMAG_PHASE_LEAVES = PARTICLE_TYPES.register("imag_phase_leaves",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VECTOR_BLAST = PARTICLE_TYPES.register("vector_blast",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_SPLASH = PARTICLE_TYPES.register("blood_splash",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_SPRAY_GROUND = PARTICLE_TYPES.register("blood_spray_ground",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_SPRAY_WALL = PARTICLE_TYPES.register("blood_spray_wall",
            () -> new SimpleParticleType(false));
}
