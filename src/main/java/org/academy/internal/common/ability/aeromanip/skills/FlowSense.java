package org.academy.internal.common.ability.aeromanip.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.FlowSensePacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

public final class FlowSense extends Skill {
    public FlowSense() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .passive()
                .iterationTicks(10)
                .dependsOn(Skills.AIRFLOW_JET)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1)));
    }

    @Override
    public void initClient() {
        FlowSensePacket.initClient();
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(Skills.FLOW_SENSE.get(), List.of(),
                        R.textures.flow_sense_icon, 75, 40));
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
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || player.level().getGameTime() % 10 != 0
                    || !Skills.FLOW_SENSE.get().isEnabled(player)) return;
            var skillLevel = Math.max(0, Math.min(2, Skills.FLOW_SENSE.get().getLevel(player)));
            var range = 12.0 + skillLevel * 4.0;
            var eye = player.getEyePosition();
            var box = new AABB(eye, eye).inflate(range);
            var entities = player.level().getEntities(player, box,
                    entity -> entity.isAlive()
                            && entity.getDeltaMovement().lengthSqr() > 0.01
                            && player.hasLineOfSight(entity));
            var sent = 0;
            for (var entity : entities) {
                if (sent++ >= 64) break;
                if (!(entity instanceof Projectile) && entity.getDeltaMovement().lengthSqr() < 0.04) continue;
                var velocity = entity.getDeltaMovement();
                var speed = velocity.length();
                if (speed > 1.0e-8 && Double.isFinite(speed)) {
                    new FlowSensePacket(entity.getId(), velocity.scale(1.0 / speed), speed).sendTo(player);
                }
                var position = entity.getBoundingBox().getCenter();
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(player, ParticleTypes.END_ROD, false, false,
                            position.x, position.y, position.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
