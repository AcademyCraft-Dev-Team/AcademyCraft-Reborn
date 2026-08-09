package org.academy.internal.common.world.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;

public final class StatusEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, MODID);

    public static final DeferredHolder<MobEffect, MobEffect> IMPRISONED =
            MOB_EFFECTS.register("imprisoned", ImprisonedEffect::new);

    private static final class ImprisonedEffect extends MobEffect {
        private ImprisonedEffect() {
            super(MobEffectCategory.HARMFUL, 0x76678F);
        }
    }

    private StatusEffects() {
    }
}
