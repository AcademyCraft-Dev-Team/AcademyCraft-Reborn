package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.util.List;

public final class BreathingFilm extends Skill {
    private static final int REFRESH_INTERVAL_TICKS = 10;

    public BreathingFilm() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .maintenanceCost(20)
                .iterationTicks(40)
                .dependsOn(Skills.ATMOSPHERE_SHIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BREATHING_FILM.get(),
                        List.of(AtmosphereShield.Client.SKILL_INFO),
                        R.textures.breathing_film_icon,
                        75,
                        40
                )
        );

        private Client() {
        }

        private static void initialize() {
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().getGameTime() % REFRESH_INTERVAL_TICKS != 0) return;

            var skill = Skills.BREATHING_FILM.get();
            var system = AbilitySystemServer.getSystem(player);
            var runtimeData = skill.getRuntimeData(player);
            var available = runtimeData.isPresent() && LearningHelper.isSkillAvailableForCategory(
                    system.getPlayerAbilityCategory(player.getUUID()),
                    skill
            );
            if (!available || !player.isAlive()) {
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }
            if (!runtimeData.orElseThrow().isEnabled()) {
                system.toggleSkill(player.getUUID(), skill.getKeyString());
            }
            if (!system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player)),
                    skill
            )) {
                return;
            }

            var maxAir = player.getMaxAirSupply();
            if (player.getAirSupply() < maxAir) {
                player.setAirSupply(maxAir);
            }
        }
    }
}
