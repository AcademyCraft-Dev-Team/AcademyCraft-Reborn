package org.academy.internal.common.core.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public class ParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMAG_PHASE_FLUID = PARTICLE_TYPES.register("imag_phase_fluid",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMAG_PHASE_LEAVES = PARTICLE_TYPES.register("imag_phase_leaves",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ARC_SMALL = PARTICLE_TYPES.register("arc_small",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ARC_MEDIUM = PARTICLE_TYPES.register("arc_medium",
            () -> new SimpleParticleType(false));
}