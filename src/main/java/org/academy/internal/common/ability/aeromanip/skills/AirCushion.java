package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;

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
            var baseCost = 12.0f * AeromanipConfig.cpMultiplier(owner, SkillNames.AIR_CUSHION);
            var cost = skill.adjustProficiencyCost(owner, SkillProficiencyProfile.CostKind.CAST, baseCost);
            if (!system.tryTimedOccupation(owner.getUUID(), cost, skill, 20)) return;
            var level = Math.max(0, Math.min(2, skill.getLevel(owner)));
            event.setDamageMultiplier(Math.max(0.0f, 1.0f - REDUCTION[level]));
            if (skill.hasProficiencyMilestone(owner, 3)) triggerAirBurst(owner, player);
        }

        private static ServerPlayer findOwner(ServerPlayer landing) {
            var skill = Skills.AIR_CUSHION.get();
            if (skill.isEnabled(landing)) return landing;
            if (landing.level() instanceof ServerLevel level) {
                for (var player : level.players()) {
                    var radius = skill.hasProficiencyMilestone(player, 2) ? 5.0 : 3.0;
                    if (player.distanceToSqr(landing) <= radius * radius
                            && skill.isEnabled(player)
                            && skill.getLevel(player) >= 2) {
                        return player;
                    }
                }
            }
            return null;
        }

        private static void triggerAirBurst(ServerPlayer owner, ServerPlayer landing) {
            var skill = Skills.AIR_CUSHION.get();
            var now = owner.level().getGameTime();
            if (TimedSkillEffectRuntime.get(owner.getUUID(), owner.getUUID(), skill, "air_burst_cooldown", now).isPresent()) {
                return;
            }
            TimedSkillEffectRuntime.put(owner, owner.getUUID(), skill, "air_burst_cooldown", 80, 1.0f);
            var center = landing.position();
            var handled = 0;
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            for (var entity : landing.level().getEntities(landing, new AABB(center, center).inflate(3.0),
                    entity -> entity.isAlive() && (entity instanceof Projectile
                            || AeromanipTargeting.canAffectNegatively(owner, entity)))) {
                if (handled++ >= cap) break;
                var direction = entity.getBoundingBox().getCenter().subtract(center);
                if (direction.lengthSqr() <= 1.0e-8) direction = new net.minecraft.world.phys.Vec3(0, 1, 0);
                entity.setDeltaMovement(direction.normalize().scale(entity instanceof Projectile ? 0.8 : 0.55)
                        .add(0, 0.18, 0));
                entity.hurtMarked = true;
            }
        }
    }
}
