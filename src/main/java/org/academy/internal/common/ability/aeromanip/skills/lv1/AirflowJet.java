package org.academy.internal.common.ability.aeromanip.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.projectile.PaperAirplane;
import org.academy.internal.server.ability.AeromanipResourceManager;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Three-stage air burst: tap attacks, half charge launches the caster, and full charge
 * clears nearby creatures before entering a short high-speed propulsion state.
 */
public final class AirflowJet extends Skill {
    static final float INSTANT_CP_COST = 8.0f;
    static final float INSTANT_AIR_COST = 16.0f;
    static final float HALF_CP_COST = 14.0f;
    static final float HALF_AIR_COST = 40.0f;
    static final float FULL_CP_COST = 24.0f;
    static final float FULL_AIR_COST = 96.0f;
    private static final double INSTANT_RANGE = 12.0;
    private static final double FULL_RADIUS = 6.0;
    private static final double HALF_LAUNCH_SPEED = 1.35;
    private static final double FULL_PROPULSION_SPEED = 2.35;
    private static final double SUBMERGED_SPEED_MULTIPLIER = 0.4;
    private static final int FULL_DURATION_TICKS = 30;
    private static final int MILESTONE_TWO_DURATION_TICKS = 40;

    public AirflowJet() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost((int) INSTANT_CP_COST)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    static float instantDamage(boolean milestoneOne) {
        return milestoneOne ? 2.0f : 1.5f;
    }

    static double halfLaunchSpeed(boolean milestoneTwo) {
        return milestoneTwo ? HALF_LAUNCH_SPEED * 1.2 : HALF_LAUNCH_SPEED;
    }

    static double fullPropulsionSpeed(boolean milestoneThree, boolean fullySubmerged) {
        var speed = milestoneThree ? FULL_PROPULSION_SPEED * 1.2 : FULL_PROPULSION_SPEED;
        return fullySubmerged ? speed * SUBMERGED_SPEED_MULTIPLIER : speed;
    }

    static int fullPropulsionDuration(boolean milestoneTwo) {
        return milestoneTwo ? MILESTONE_TWO_DURATION_TICKS : FULL_DURATION_TICKS;
    }

    private static boolean isFullySubmerged(ServerPlayer player) {
        var eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        var eyeFluid = player.level().getFluidState(eyePos);
        if (eyeFluid.isEmpty()
                || player.getEyeY() >= eyePos.getY() + eyeFluid.getHeight(player.level(), eyePos)) {
            return false;
        }
        return !player.level().getFluidState(player.blockPosition()).isEmpty();
    }

    @Override
    public void initClient() {
        AeromanipFieldSyncPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_R,
                InputSystem.ANY_ACTION,
                0
        );
        var configuredBinding = Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST, defaultBinding);
        if (configuredBinding.action() != InputSystem.ANY_ACTION
                || configuredBinding.type() == InputSystem.InputType.KEYBOARD
                && configuredBinding.keys().equals(Set.of(InputConstants.KEY_R))
                && configuredBinding.modifiers() == InputSystem.ANY_MODIFIER) {
            configuredBinding = new InputSystem.KeyCombination(
                    configuredBinding.type(),
                    configuredBinding.keys(),
                    InputSystem.ANY_ACTION,
                    configuredBinding.type() == InputSystem.InputType.KEYBOARD
                            && configuredBinding.keys().equals(Set.of(InputConstants.KEY_R))
                            && configuredBinding.modifiers() == InputSystem.ANY_MODIFIER
                            ? 0
                            : configuredBinding.modifiers(),
                    configuredBinding.availableWhenScreen(),
                    configuredBinding.unbound()
            );
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, configuredBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST,
                configuredBinding,
                _ -> Client.start(),
                _ -> Client.stop()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.AIRFLOW_JET.get(),
                        List.of(),
                        R.textures.airflow_jet_icon,
                        20,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.AIRFLOW_JET + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (!AbilitySystemClient.canUseSkill(Skills.AIRFLOW_JET.get())) return;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            MisakaNetworkClient.send(StopPacket.INSTANCE);
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
        private static final Map<ServerPlayer, ChargeContext> CHARGES = new WeakHashMap<>();
        private static final Map<ServerPlayer, PropulsionContext> PROPULSION = new WeakHashMap<>();
        private static final Map<ServerPlayer, Double> MACE_MOMENTUM = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.AIRFLOW_JET.get();
            if (CHARGES.containsKey(player) || PROPULSION.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new ChargeContext(player);
            CHARGES.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CHARGES.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
        }

        public static boolean isActive(ServerPlayer player) {
            return CHARGES.containsKey(player) || PROPULSION.containsKey(player);
        }

        public static double getEffectiveMaceFallDistance(Entity entity) {
            if (!(entity instanceof ServerPlayer player)) return entity.fallDistance;
            return Math.max(player.fallDistance, MACE_MOMENTUM.getOrDefault(player, 0.0));
        }

        public static void consumeMaceMomentum(ServerPlayer player) {
            MACE_MOMENTUM.remove(player);
        }

        static double accumulateMaceMomentum(double current, double fallDistance, Vec3 velocity) {
            var baseline = Math.max(0.0, Math.max(current, fallDistance));
            if (velocity == null) return baseline;
            var speed = velocity.length();
            if (!Double.isFinite(speed) || speed <= 0.0) return baseline;
            return Math.min(Double.MAX_VALUE, baseline + speed);
        }

        private static void recordMaceMomentum(ServerPlayer player) {
            MACE_MOMENTUM.put(player, accumulateMaceMomentum(
                    MACE_MOMENTUM.getOrDefault(player, 0.0),
                    player.fallDistance,
                    player.getDeltaMovement()
            ));
        }

        private static void clearMaceMomentum(ServerPlayer player) {
            MACE_MOMENTUM.remove(player);
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.AIRFLOW_JET.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                var skill = Skills.AIRFLOW_JET.get();
                switch (tier) {
                    case INSTANT -> skill.executeActiveWithResource(
                            player,
                            _ -> cpCost(player, INSTANT_CP_COST),
                            _ -> INSTANT_AIR_COST,
                            (_, _) -> castInstant(player, skill));
                    case HALF -> skill.executeActiveWithResource(
                            player,
                            _ -> cpCost(player, HALF_CP_COST),
                            _ -> HALF_AIR_COST,
                            (_, _) -> castHalf(player, skill));
                    case FULL -> skill.executeActiveWithResource(
                            player,
                            _ -> cpCost(player, FULL_CP_COST),
                            _ -> FULL_AIR_COST,
                            (_, _) -> castFull(player, skill));
                }
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                var pitch = tier == AeromanipChargeTier.FULL ? 1.45f : 1.2f;
                player.level().playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                        SoundSource.PLAYERS, 0.45f, pitch);
                AeromanipVfx.ring(player.level(),
                        player.position().add(0.0, 0.15, 0.0),
                        tier == AeromanipChargeTier.FULL ? 1.2 : 0.7);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static float cpCost(ServerPlayer player, float baseCost) {
            return baseCost * AeromanipConfig.cpMultiplier(player, SkillNames.AIRFLOW_JET);
        }

        private static void castInstant(ServerPlayer player, AirflowJet skill) {
            var target = rayTarget(player);
            if (target instanceof PaperAirplane airplane && airplane.boost(player, player.getLookAngle())) {
                player.level().playSound(null, airplane.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                        SoundSource.PLAYERS, 0.75f, 1.55f);
                AeromanipVfx.stream(player.level(), airplane.position(),
                        player.getLookAngle(), 2.0);
                return;
            }
            if (target instanceof LivingEntity living) {
                var damage = instantDamage(skill.hasProficiencyMilestone(player, 1))
                        * AeromanipConfig.damageMultiplier(player, SkillNames.AIRFLOW_JET)
                        * AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, skill);
                living.hurtServer(player.level(), source, damage);
                var force = AeromanipTargeting.forceMultiplier(player, living);
                if (force > 0.0) {
                    var strength = skill.hasProficiencyMilestone(player, 1) ? 1.4 : 1.1;
                    AeromanipTargeting.addClampedVelocity(living,
                            player.getLookAngle().normalize().scale(strength * force).add(0.0, 0.12, 0.0));
                }
            }
            var eye = player.getEyePosition();
            var look = player.getLookAngle().normalize();
            AeromanipVfx.stream(player.level(), eye.add(look.scale(0.35)), look,
                    INSTANT_RANGE * AeromanipConfig.rangeMultiplier(player, SkillNames.AIRFLOW_JET));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                    SoundSource.PLAYERS, 0.65f, 1.25f);
        }

        private static Entity rayTarget(ServerPlayer player) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-8) return null;
            var end = eye.add(look.normalize().scale(INSTANT_RANGE
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.AIRFLOW_JET)));
            var blockHit = player.level().clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            var rayEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(
                    player.level(), player, eye, rayEnd,
                    new AABB(eye, rayEnd).inflate(0.8),
                    entity -> entity instanceof PaperAirplane && !entity.isRemoved()
                            || entity instanceof LivingEntity living && living.isAlive()
                            && AeromanipTargeting.canAffectNegatively(player, living),
                    0.3f);
            return entityHit == null ? null : entityHit.getEntity();
        }

        private static void castHalf(ServerPlayer player, AirflowJet skill) {
            var direction = player.getLookAngle();
            if (direction.lengthSqr() <= 1.0e-8) return;
            AeromanipTargeting.steerVelocity(
                    player,
                    direction.add(0.0, 0.06, 0.0),
                    1.0,
                    halfLaunchSpeed(skill.hasProficiencyMilestone(player, 2)));
            recordMaceMomentum(player);
            player.resetFallDistance();
            AeromanipVfx.stream(player.level(),
                    player.position().add(0.0, player.getBbHeight() * 0.45, 0.0),
                    direction, 4.5);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        private static void castFull(ServerPlayer player, AirflowJet skill) {
            var level = player.level();
            var center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
            var range = FULL_RADIUS * AeromanipConfig.rangeMultiplier(player, SkillNames.AIRFLOW_JET);
            var bounds = new AABB(center, center).inflate(range);
            for (var living : level.getEntitiesOfClass(LivingEntity.class, bounds,
                    living -> AeromanipTargeting.canAffectNegatively(player, living))) {
                var delta = living.getBoundingBox().getCenter().subtract(center);
                if (delta.lengthSqr() > range * range) continue;
                if (delta.lengthSqr() <= 1.0e-8) delta = new Vec3(0.0, 1.0, 0.0);
                var force = AeromanipTargeting.forceMultiplier(player, living);
                if (force > 0.0) {
                    AeromanipTargeting.addClampedVelocity(living,
                            delta.normalize().scale(1.25 * force).add(0.0, 0.2 * force, 0.0));
                }
            }
            AeromanipVfx.burst(level, center, range * 0.72);
            AeromanipVfx.ring(level, center, range);
            level.playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            var previous = PROPULSION.remove(player);
            if (previous != null) previous.stop();
            var propulsion = new PropulsionContext(player, skill);
            PROPULSION.put(player, propulsion);
            AbilitySystemServer.registerContext(propulsion);
        }

        private static final class PropulsionContext extends ServerContext {
            private final ServerLevel initialLevel;
            private final AirflowJet skill;
            private final AeromanipResourceManager.UsageLease usageLease;
            private final int durationTicks;
            private int ticks;
            private boolean ended;

            private PropulsionContext(ServerPlayer player, AirflowJet skill) {
                super(player);
                this.skill = skill;
                initialLevel = player.level();
                usageLease = AbilitySystemServer.getSystem(player)
                        .getAeromanipResourceManager().beginUse(player);
                durationTicks = fullPropulsionDuration(skill.hasProficiencyMilestone(player, 2));
            }

            private void stop() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                if (ended || ticks >= durationTicks || player.hasDisconnected() || !player.isAlive()
                        || player.level() != initialLevel || !skill.isEnabled(player)) {
                    stop();
                    return;
                }
                var direction = player.getLookAngle().add(0.0, 0.08, 0.0);
                AeromanipTargeting.steerVelocity(
                        player,
                        direction,
                        0.48,
                        fullPropulsionSpeed(skill.hasProficiencyMilestone(player, 3),
                                isFullySubmerged(player)));
                recordMaceMomentum(player);
                player.resetFallDistance();
                skill.reportActivity(player, true);
                if (ticks % 6 == 0) {
                    AeromanipVfx.stream(initialLevel,
                            player.position().add(0.0, player.getBbHeight() * 0.45, 0.0),
                            direction.scale(-1.0), 2.8);
                }
                ticks++;
            }

            @Override
            protected void onUnregistered() {
                ended = true;
                usageLease.close();
                PROPULSION.remove(player, this);
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!player.isAlive() || player.hasDisconnected() || player.onGround() || player.isInWater()) {
                Server.clearMaceMomentum(player);
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
            return PacketTypes.AIRFLOW_JET_START.get();
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
            return PacketTypes.AIRFLOW_JET_STOP.get();
        }
    }
}
