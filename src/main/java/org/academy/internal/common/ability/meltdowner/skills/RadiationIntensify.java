package org.academy.internal.common.ability.meltdowner.skills;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;

import java.util.List;

public final class RadiationIntensify extends Skill {
    public static final float MARK_DAMAGE_MULTIPLIER = 1.5f;
    public static final long MARK_DURATION_TICKS = 200L;
    public static final String TARGET_MARK_UNTIL_KEY = "academy_md_mark_until";

    public RadiationIntensify() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .passive()
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Single High-Speed Electron Beam",
                        "academy:single_high_speed_electron_beam"
                ))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    public static boolean isMarked(LivingEntity target, long gameTime) {
        return target.getPersistentData().getLong(TARGET_MARK_UNTIL_KEY).orElse(0L) > gameTime;
    }

    public static void mark(LivingEntity target, long gameTime) {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0, false, false, true));
        target.getPersistentData().putLong(TARGET_MARK_UNTIL_KEY, markExpiry(gameTime));
    }

    public static float amplifyDamage(float damage, boolean marked) {
        return MeltdownerBeamDamage.amplify(damage, marked);
    }

    static long markExpiry(long gameTime) {
        return gameTime > Long.MAX_VALUE - MARK_DURATION_TICKS
                ? Long.MAX_VALUE
                : gameTime + MARK_DURATION_TICKS;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.RADIATION_INTENSIFY.get(),
                        List.of(SingleHighSpeedElectronBeam.Client.SKILL_INFO),
                        R.textures.radiation_intensify_icon,
                        11,
                        17.5f
                )
        );

        private Client() {
        }

        private static void initialize() {
        }
    }
}
