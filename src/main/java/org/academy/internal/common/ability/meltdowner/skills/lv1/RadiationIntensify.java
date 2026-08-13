package org.academy.internal.common.ability.meltdowner.skills.lv1;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
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
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Single High-Speed Electron Beam",
                        "academy:single_high_speed_electron_beam"
                ))
        );
    }

    public static boolean isMarked(LivingEntity target, long gameTime) {
        return target.getPersistentData().getLong(TARGET_MARK_UNTIL_KEY).orElse(0L) > gameTime;
    }

    public static void mark(LivingEntity target, long gameTime) {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0, false, false, true));
        target.getPersistentData().putLong(TARGET_MARK_UNTIL_KEY, markExpiry(gameTime));
    }

    public static void mark(ServerPlayer owner, LivingEntity target, long gameTime) {
        var skill = Skills.RADIATION_INTENSIFY.get();
        var milestone = skill.getEffectiveProficiencyMilestone(owner);
        var duration = milestone >= 1 ? 240 : (int) MARK_DURATION_TICKS;
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration / 2, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration / 2, 0, false, false, true));
        target.getPersistentData().putLong(TARGET_MARK_UNTIL_KEY,
                gameTime > Long.MAX_VALUE - duration ? Long.MAX_VALUE : gameTime + duration);
        TimedSkillEffectRuntime.put(owner, target.getUUID(), skill, "radiation_mark", duration, milestone);
    }

    public static float amplifyDamage(float damage, boolean marked) {
        return MeltdownerBeamDamage.amplify(damage, marked);
    }

    static long markExpiry(long gameTime) {
        return gameTime > Long.MAX_VALUE - MARK_DURATION_TICKS
                ? Long.MAX_VALUE
                : gameTime + MARK_DURATION_TICKS;
    }

    @Override
    public void initClient() {
        Client.initialize();
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

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() { }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) return;
            var skill = Skills.RADIATION_INTENSIFY.get();
            var now = level.getGameTime();
            var sourceId = TimedSkillEffectRuntime.sourceForTarget(event.getEntity().getUUID(), skill,
                    "radiation_mark", now).orElse(null);
            if (sourceId == null) return;
            var owner = level.getServer().getPlayerList().getPlayer(sourceId);
            if (owner == null) return;
            var entry = TimedSkillEffectRuntime.get(sourceId, event.getEntity().getUUID(), skill,
                    "radiation_mark", now).orElse(null);
            if (entry == null || entry.value() < 3.0f) return;
            var remaining = Math.max(1, (int) Math.min(Integer.MAX_VALUE, entry.expiresAt() - now));
            var spread = 0;
            for (var target : level.getEntitiesOfClass(LivingEntity.class,
                    event.getEntity().getBoundingBox().inflate(5.0),
                    target -> target.isAlive() && target != owner && !owner.isAlliedTo(target))) {
                if (spread++ >= 3) break;
                target.getPersistentData().putLong(TARGET_MARK_UNTIL_KEY, now + remaining);
                TimedSkillEffectRuntime.put(owner, target.getUUID(), skill,
                        "radiation_mark", remaining, 3.0f);
            }
        }
    }
}
