package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;

import java.util.List;

public final class AirCushion extends Skill {
    private static final float[] REDUCTION = {0.70f, 0.85f, 1.0f};

    public AirCushion() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.AIRFLOW_JET)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1)));
    }

    @Override
    public void initClient() {
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(Skills.AIR_CUSHION.get(), List.of(AirflowJet.Client.SKILL_INFO),
                        R.textures.air_cushion_icon, 130, 40));
    }

    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO;

        private Client() {
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onFall(LivingFallEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.AIR_CUSHION.get();
            var owner = findOwner(player);
            if (owner == null || event.getDistance() < 7.0f) return;
            var system = AbilitySystemServer.getSystem(owner);
            if (!system.tryTimedOccupation(owner.getUUID(),
                    12.0f * AeromanipConfig.cpMultiplier(owner, SkillNames.AIR_CUSHION), skill, 20)) return;
            var level = Math.max(0, Math.min(2, skill.getLevel(owner)));
            event.setDamageMultiplier(Math.max(0.0f, 1.0f - REDUCTION[level]));
        }

        private static ServerPlayer findOwner(ServerPlayer landing) {
            var skill = Skills.AIR_CUSHION.get();
            if (skill.isEnabled(landing)) return landing;
            if (landing.level() instanceof net.minecraft.server.level.ServerLevel level) {
                for (var player : level.players()) {
                    if (player.distanceToSqr(landing) <= 9.0
                            && skill.isEnabled(player)
                            && skill.getLevel(player) >= 2) {
                        return player;
                    }
                }
            }
            return null;
        }
    }
}
