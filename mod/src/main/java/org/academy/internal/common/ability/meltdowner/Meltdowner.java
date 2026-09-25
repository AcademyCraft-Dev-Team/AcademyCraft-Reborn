package org.academy.internal.common.ability.meltdowner;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.Optional;

public final class Meltdowner extends AbilityCategory {
    private static final int RADIATION_TICKS = 200;

    public void onDamageCompleted(LivingEntity target, DamageContainer hit, float healthDamage) {
        if (!(hit.getSource() instanceof SkillDamageSource source)
                || source.getSkill().getCategory() != this
                || !(healthDamage > 0.0f)) return;
        applyRadiation(target);
    }

    public void applyRadiation(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive()) return;
        for (var effect : java.util.List.of(MobEffects.NAUSEA, MobEffects.SLOWNESS,
                MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS)) {
            target.addEffect(new MobEffectInstance(effect, RADIATION_TICKS, 0));
        }
    }

    public Meltdowner() {
        super(0.1F, AbilityDevelopmentProfiles.MELTDOWNER);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.MELT_DAMAGE);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ability.meltdowner.icon;
    }

    @Override
    public String getDisplayName() {
        return "Meltdowner";
    }
}
