package org.academy.internal.common.ability.aeromanip.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AeromanipResourceManager;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class PneumaticGrasp extends Skill {
    private static final double MIN_CONTROL_DISTANCE = 2.0;
    private static final double DEFAULT_CONTROL_DISTANCE = 2.5;
    private static final double DISTANCE_STEP = 1.0;

    public PneumaticGrasp() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.FLOW_SENSE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, 0)), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_STOP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, 0)), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(Skills.PNEUMATIC_GRASP.get(), List.of(FlowSense.Client.SKILL_INFO),
                        R.textures.pneumatic_grasp_icon, 75, 72));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_START = SkillNames.PNEUMATIC_GRASP + "_start";
        public static final String KEY_NAME_STOP = SkillNames.PNEUMATIC_GRASP + "_stop";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();
        private static ControlContext currentContext;

        private static void start() {
            if (currentContext != null || !AbilitySystemClient.canUseSkill(Skills.PNEUMATIC_GRASP.get())) return;
            currentContext = new ControlContext();
            AbilitySystemClient.registerContext(currentContext);
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            if (currentContext != null) currentContext.cleanup();
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        private static final class ControlContext extends ClientContext {
            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                var steps = Mth.sign(event.yOffset);
                if (steps == 0) return;
                MisakaNetworkClient.send(new AdjustDistancePacket(steps));
                event.setCanceled(true);
            }

            private void cleanup() {
                unregister();
                if (currentContext == this) currentContext = null;
            }
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
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
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player) || !Skills.PNEUMATIC_GRASP.get().isEnabled(player)) return;
            var context = new Context(player);
            ACTIVE.put(player, context);
            AbilitySystemServer.registerContext(context);
            Skills.PNEUMATIC_GRASP.get().reportTrigger(player);
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        @SubscribePacket
        public static void handle(AdjustDistancePacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null) context.adjustDistance(packet.steps());
        }

        private static final class Context extends ServerContext {
            private final ResourceKey<Level> dimension;
            private final AeromanipResourceManager.UsageLease usageLease;
            private Entity controlledTarget;
            private double controlledSpeedCap;
            private double holdDistance = DEFAULT_CONTROL_DISTANCE;
            private int activeTicks;
            private boolean ended;

            private Context(ServerPlayer player) {
                super(player);
                dimension = player.level().dimension();
                usageLease = AbilitySystemServer.getSystem(player)
                        .getAeromanipResourceManager().beginUse(player);
            }

            private void end() {
                if (!ended) {
                    ended = true;
                    unregister();
                }
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                var skill = Skills.PNEUMATIC_GRASP.get();
                if (ended || player.hasDisconnected() || !player.isAlive()
                        || !player.level().dimension().equals(dimension) || !skill.isEnabled(player)) {
                    end();
                    return;
                }
                skill.reportActivity(player, false);
                var eye = player.getEyePosition();
                var look = player.getLookAngle().normalize();
                if (look.lengthSqr() <= 1.0e-8) return;
                var skillLevel = Math.max(0, Math.min(2, skill.getLevel(player)));
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var range = 16.0 + skillLevel * 4.0 + (milestone >= 1 ? 8.0 : 0.0);
                if (!isValidTarget(controlledTarget, skillLevel, range)) {
                    controlledTarget = findTarget(eye, look, skillLevel, range);
                    controlledSpeedCap = controlledTarget instanceof Projectile
                            ? Math.max(0.1, controlledTarget.getDeltaMovement().length())
                            : 0.0;
                }
                if (controlledTarget == null) return;
                activeTicks++;
                if (activeTicks % 10 == 0 && !skill.executeContinuousWithResource(
                        player,
                        _ -> 10.0f * AeromanipConfig.cpMultiplier(player, SkillNames.PNEUMATIC_GRASP),
                        _ -> Math.max(0.0f, AeromanipConfig.skillFloat(
                                player, SkillNames.PNEUMATIC_GRASP,
                                "compressedAirPerInterval", 8.0f)),
                        (_, _) -> { },
                        true)) {
                    end();
                    return;
                }
                if (milestone >= 3 && controlledTarget instanceof LivingEntity living
                        && living.onGround() && activeTicks % 5 == 0
                        && !skill.executeContinuousWithResource(
                        player,
                        _ -> 5.0f * AeromanipConfig.cpMultiplier(player, SkillNames.PNEUMATIC_GRASP),
                        _ -> 2.0f,
                        (_, _) -> { },
                        true)) {
                    end();
                    return;
                }
                EntityMotionGuard.runWithMotionSource(
                        player,
                        () -> moveTarget(controlledTarget, eye, look, skillLevel));
                skill.reportActivity(player, true);
            }

            private Entity findTarget(Vec3 eye, Vec3 look,
                                      int skillLevel, double range) {
                var end = eye.add(look.scale(range));
                var blockHit = player.level().clip(new ClipContext(
                        eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                var blockDistance = blockHit.getType() == HitResult.Type.MISS
                        ? range * range
                        : eye.distanceToSqr(blockHit.getLocation());
                var hit = ProjectileUtil.getEntityHitResult(
                        player.level(), player, eye, end,
                        player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.25),
                        entity -> isControllable(entity, skillLevel),
                        0.8f);
                if (hit == null || eye.distanceToSqr(hit.getLocation()) >= blockDistance) return null;
                return hit.getEntity();
            }

            private boolean isValidTarget(Entity target, int skillLevel, double range) {
                return isControllable(target, skillLevel)
                        && target.level() == player.level()
                        && target.distanceToSqr(player) <= (range + 2.0) * (range + 2.0)
                        && player.hasLineOfSight(target);
            }

            private boolean isControllable(Entity entity, int skillLevel) {
                if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator()) return false;
                if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile) {
                    return true;
                }
                if (!(entity instanceof LivingEntity living)
                        || AeromanipTargeting.isBoss(living)
                        || !AeromanipTargeting.canAffectNegatively(player, living)) {
                    return false;
                }
                return entity instanceof Enemy
                        || skillLevel >= 2 && !living.onGround()
                        || Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 3);
            }

            private void adjustDistance(int steps) {
                if (steps == 0) return;
                var skillLevel = Math.max(0, Math.min(2, Skills.PNEUMATIC_GRASP.get().getLevel(player)));
                var maxDistance = 12.0 + skillLevel * 2.0;
                holdDistance = AeromanipTargeting.adjustControlDistance(
                        holdDistance, steps, DISTANCE_STEP, MIN_CONTROL_DISTANCE, maxDistance);
            }

            private void moveTarget(Entity target, Vec3 eye, Vec3 look, int skillLevel) {
                var targetCenter = target.getBoundingBox().getCenter();
                var destination = player.isShiftKeyDown()
                        ? targetCenter.add(targetCenter.subtract(eye).normalize().scale(4.0))
                        : eye.add(look.scale(holdDistance));
                var delta = destination.subtract(targetCenter);
                if (delta.lengthSqr() <= 0.12 * 0.12) {
                    AeromanipTargeting.scaleVelocity(target, 0.3);
                    spawnEffect(target, eye, destination);
                    return;
                }
                var lightTarget = target instanceof ItemEntity
                        || target instanceof ExperienceOrb
                        || target instanceof Projectile;
                var hostileLiving = target instanceof Enemy;
                var forceMultiplier = AeromanipTargeting.forceMultiplier(player, target);
                if (target instanceof LivingEntity living && living.onGround()
                        && Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 3)) {
                    forceMultiplier *= 0.5;
                }
                if (forceMultiplier <= 0.0) return;
                var response = (lightTarget ? 0.52 : hostileLiving ? 0.28 + skillLevel * 0.04 : 0.3)
                        * forceMultiplier;
                if (!lightTarget && Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 2)) response *= 1.2;
                var projectileSpeed = target instanceof Projectile ? Math.max(0.1, controlledSpeedCap) : 1.35;
                var targetSpeed = (lightTarget ? projectileSpeed : hostileLiving ? 0.6 + skillLevel * 0.1 : 0.7)
                        * forceMultiplier;
                AeromanipTargeting.steerVelocity(target, delta, response, targetSpeed);
                target.resetFallDistance();
                spawnEffect(target, eye, destination);
            }

            private void spawnEffect(Entity target, Vec3 eye, Vec3 destination) {
                if (!(player.level() instanceof ServerLevel level) || activeTicks % 6 != 0) return;
                var targetCenter = target.getBoundingBox().getCenter();
                var beam = targetCenter.subtract(eye);
                if (beam.lengthSqr() > 1.0e-8) {
                    AeromanipVfx.stream(level, eye, beam, beam.length());
                }
                AeromanipVfx.vortex(level, targetCenter,
                        Math.max(0.4, target.getBbWidth() * 0.7));
                AeromanipVfx.burst(level, destination, 0.32);
            }

            @Override
            protected void onUnregistered() {
                controlledTarget = null;
                controlledSpeedCap = 0.0;
                usageLease.close();
                ACTIVE.remove(player, this);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.PNEUMATIC_GRASP_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.PNEUMATIC_GRASP_STOP.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class AdjustDistancePacket extends Packet<ServerGamePacketListenerImpl, AdjustDistancePacket> {
        public static final StreamCodec<ByteBuf, AdjustDistancePacket> CODEC = ByteBufCodecs.VAR_INT.map(
                AdjustDistancePacket::new, AdjustDistancePacket::steps);
        private final int steps;

        public AdjustDistancePacket(int steps) {
            this.steps = steps;
        }

        public int steps() {
            return steps;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AdjustDistancePacket> getPacketType() {
            return PacketTypes.PNEUMATIC_GRASP_ADJUST_DISTANCE.get();
        }
    }
}
