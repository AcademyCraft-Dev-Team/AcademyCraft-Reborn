package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public class BloodflowReverse extends Skill {
    public static final double RANGE_BONUS = 2.0;
    public static final double TARGET_BOX_INFLATE = 0.2;
    public static final double SEARCH_HALF_WIDTH = 0.85;
    public static final double SEARCH_HALF_HEIGHT = 1.15;
    private static final double BLOOD_SPRAY_RANGE = 5.0;
    private static final double BLOOD_SPRAY_SURFACE_OFFSET = 0.015;
    private static final float[] BLOOD_SPRAY_PITCHES = {
            0.0f, 30.0f, 45.0f, 60.0f, 80.0f, -30.0f, -45.0f, -60.0f, -80.0f
    };

    public BloodflowReverse() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(100)
                .iterationTicks(20)
                .maxStacks(2)
                .dependsOn(Skills.VECTOR_REFLECTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Vector Reflection", "academy:vector_reflection"))
        );
    }

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        var dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        var dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        var dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME, Client.CONFIG.getKeyBinding(Client.KEY_NAME,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.reverseBloodflow());
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(Skills.BLOODFLOW_REVERSE.get(), List.of(VectorReflection.Client.SKILL_INFO), R.textures.ability.accelerator.skill.bloodflow_reverse.icon, 204, 83)
        );
        public static final String KEY_NAME = SkillNames.BLOODFLOW_REVERSE + "_use";
        public static Config CONFIG = new Config();

        public static void reverseBloodflow() {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || findTarget(
                    player,
                    minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)
            ) == null) {
                return;
            }
            MisakaNetworkClient.send(ReverseBloodflowPacket.INSTANCE);
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null
                    || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.BLOODFLOW_REVERSE.get())
                    || !isPreviewing()) {
                return;
            }
            var target = findTarget(player, event.getPartialTick());
            if (target == null) return;

            var renderType = Render.RenderTypes.MINE_DETECT_LINES;
            var camera = minecraft.gameRenderer.mainCamera().position();
            var bounds = target.getBoundingBox().inflate(TARGET_BOX_INFLATE);
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(renderType, (snapshot, consumer) ->
                    LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, bounds,
                            1.0f, 0.15f, 0.15f, 1.0f
                    ));
            matrices.popPose();
        }

        private static boolean isPreviewing() {
            return InputSystem.isDown(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT)
                    && (InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_LALT)
                    || InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_RALT));
        }

        private static LivingEntity findTarget(LocalPlayer player, float partialTick) {
            var eyePosition = player.getEyePosition(partialTick);
            var direction = player.getViewVector(partialTick).normalize();
            if (direction.lengthSqr() < 1.0e-6) return null;
            var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            var range = RANGE_BONUS + (reach == null ? 0.0 : Math.max(0.0, reach.getValue()));
            var end = eyePosition.add(direction.scale(range));
            var searchBox = new AABB(eyePosition, end)
                    .inflate(SEARCH_HALF_WIDTH, SEARCH_HALF_HEIGHT, SEARCH_HALF_WIDTH);

            LivingEntity best = null;
            var bestProjection = Double.MAX_VALUE;
            for (var candidate : player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
                var candidateBox = candidate.getBoundingBox().inflate(TARGET_BOX_INFLATE);
                var projection = candidateBox.getCenter().subtract(eyePosition).dot(direction);
                if (projection < 0.0 || projection > range || !player.hasLineOfSight(candidate)) continue;
                var closestPoint = eyePosition.add(direction.scale(projection));
                if (distanceToBoxSqr(closestPoint, candidateBox)
                        > TARGET_BOX_INFLATE * TARGET_BOX_INFLATE) continue;
                if (projection < bestProjection) {
                    bestProjection = projection;
                    best = candidate;
                }
            }
            return best;
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BloodflowReverse.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private static final String EFFECT_KEY = "bloodflow_reverse_level";

        public static float calculateDamage(float maxHealth) {
            return Math.max(0.0f, maxHealth);
        }

        @SubscribePacket
        public static void onAction(ReverseBloodflowPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var target = findTarget(player);
            if (target == null) return;
            Skills.BLOODFLOW_REVERSE.get().executeActive(
                    player,
                    context -> Math.max(
                            100.0f,
                            context.system().getPlayerMaxCP(player.getUUID()) * 0.2f
                    ),
                    (ctx, actualCost) -> {
                var serverLevel = player.level();
                if (!(serverLevel instanceof ServerLevel)) return;
                if (!target.isAlive() || target.isRemoved() || target.level() != serverLevel) return;

                var currentStacks = getBloodflowStacks(target);
                var newStacks = currentStacks + 1;
                var amplifier = Math.min(newStacks - 1, 4);
                var skill = Skills.BLOODFLOW_REVERSE.get();
                var duration = skill.hasProficiencyMilestone(player, 2) ? 250 : 200;

                var damage = calculateDamage(target.getMaxHealth());
                var damaged = target.hurtServer(serverLevel,
                        SkillDamageSource.of(
                                player,
                                Skills.BLOODFLOW_REVERSE.get(),
                                DamageTypes.VEC
                        ), damage);
                if (!damaged) return;

                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, amplifier));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
                target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, amplifier));

                serverLevel.sendParticles(ParticleTypes.BLOOD_SPLASH.get(),
                        target.getX(), target.getY(0.6), target.getZ(),
                        12, 0.4, 0.55, 0.4, 0.12);
                spawnSurfaceBloodSprays(serverLevel, player, target);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOODFLOW_REVERSE.get(), SoundSource.AMBIENT, 1.0f, 1.0f);

                setBloodflowStacks(target, newStacks);
                if (skill.hasProficiencyMilestone(player, 3)) {
                    var reduction = target instanceof net.minecraft.world.entity.player.Player
                            || org.academy.internal.common.ability.aeromanip.AeromanipTargeting.isBoss(target)
                            ? 0.25f
                            : 0.5f;
                    TimedSkillEffectRuntime.put(
                            player, target.getUUID(), skill, "healing_reduction", 200, reduction);
                }
            });
        }

        private static void spawnSurfaceBloodSprays(ServerLevel level, ServerPlayer player, LivingEntity target) {
            var origin = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            for (var pitch : BLOOD_SPRAY_PITCHES) {
                var yaw = player.getYRot() + level.getRandom().nextFloat() * 40.0f - 20.0f;
                var direction = Vec3.directionFromRotation(pitch, yaw).normalize();
                var from = origin.subtract(direction.scale(0.5));
                var to = origin.add(direction.scale(BLOOD_SPRAY_RANGE));
                var hit = level.clip(new ClipContext(
                        from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target
                ));
                if (hit.getType() != HitResult.Type.BLOCK) continue;

                var face = hit.getDirection();
                var normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                var position = hit.getLocation().add(normal.scale(BLOOD_SPRAY_SURFACE_OFFSET));
                var particle = face.getAxis().isVertical()
                        ? ParticleTypes.BLOOD_SPRAY_GROUND.get()
                        : ParticleTypes.BLOOD_SPRAY_WALL.get();
                var count = 2 + level.getRandom().nextInt(2);
                for (var i = 0; i < count; i++) {
                    var markPosition = position.add(randomSurfaceOffset(level, face));
                    level.sendParticles(particle, markPosition.x, markPosition.y, markPosition.z,
                            0, normal.x, normal.y, normal.z, 1.0);
                }
            }
        }

        private static Vec3 randomSurfaceOffset(ServerLevel level, Direction face) {
            var first = (level.getRandom().nextDouble() - 0.5) * 0.18;
            var second = (level.getRandom().nextDouble() - 0.5) * 0.18;
            return switch (face.getAxis()) {
                case X -> new Vec3(0.0, first, second);
                case Y -> new Vec3(first, 0.0, second);
                case Z -> new Vec3(first, second, 0.0);
            };
        }

        private static LivingEntity findTarget(ServerPlayer player) {
            var eyePosition = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            if (direction.lengthSqr() < 1.0e-6) return null;
            var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            var range = RANGE_BONUS + (reach == null ? 0.0 : Math.max(0.0, reach.getValue()));
            if (Skills.BLOODFLOW_REVERSE.get().hasProficiencyMilestone(player, 2)) range += 4.0;
            var end = eyePosition.add(direction.scale(range));
            var searchBox = new AABB(eyePosition, end)
                    .inflate(SEARCH_HALF_WIDTH, SEARCH_HALF_HEIGHT, SEARCH_HALF_WIDTH);

            LivingEntity best = null;
            var bestProjection = Double.MAX_VALUE;
            for (var candidate : player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
                var candidateBox = candidate.getBoundingBox().inflate(TARGET_BOX_INFLATE);
                var center = candidateBox.getCenter();
                var projection = center.subtract(eyePosition).dot(direction);
                if (projection < 0.0 || projection > range || !player.hasLineOfSight(candidate)) continue;
                var closestPoint = eyePosition.add(direction.scale(projection));
                if (distanceToBoxSqr(closestPoint, candidateBox) > TARGET_BOX_INFLATE * TARGET_BOX_INFLATE) continue;
                if (projection < bestProjection) {
                    bestProjection = projection;
                    best = candidate;
                }
            }
            return best;
        }

        private static double distanceToBoxSqr(Vec3 point, AABB box) {
            var dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
            var dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
            var dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
            return dx * dx + dy * dy + dz * dz;
        }

        private static int getBloodflowStacks(LivingEntity entity) {
            var data = entity.getPersistentData();
            if (data.contains(EFFECT_KEY)) {
                var val = data.getInt(EFFECT_KEY);
                if (val.isPresent()) return val.get();
            }
            return 0;
        }

        private static void setBloodflowStacks(LivingEntity entity, int stacks) {
            var data = entity.getPersistentData();
            data.putInt(EFFECT_KEY, stacks);
        }

        public static float adjustHealing(LivingEntity entity, float amount) {
            if (entity == null || !(amount > 0.0f) || entity.level().isClientSide()) return amount;
            var reduction = TimedSkillEffectRuntime.maxValueForTarget(
                    entity.getUUID(),
                    Skills.BLOODFLOW_REVERSE.get(),
                    "healing_reduction",
                    entity.level().getGameTime()
            );
            reduction = Math.max(reduction, TimedSkillEffectRuntime.maxValueForTarget(
                    entity.getUUID(),
                    Skills.CROSSING_THE_ABYSS.get(),
                    "healing_reduction",
                    entity.level().getGameTime()
            ));
            return amount * (1.0f - Math.clamp(reduction, 0.0f, 1.0f));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReverseBloodflowPacket extends Packet<ServerGamePacketListenerImpl, ReverseBloodflowPacket> {
        public static final ReverseBloodflowPacket INSTANCE = new ReverseBloodflowPacket();
        public static final StreamCodec<ByteBuf, ReverseBloodflowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReverseBloodflowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReverseBloodflowPacket> getPacketType() {
            return PacketTypes.REVERSE_BLOODFLOW.get();
        }
    }
}
