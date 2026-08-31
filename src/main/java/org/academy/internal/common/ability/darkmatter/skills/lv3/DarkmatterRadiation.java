package org.academy.internal.common.ability.darkmatter.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.ViewTargetScanner;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterLawMark;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterPhaseTuning;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DarkmatterRadiation extends Skill {
    static final double RANGE = 32.0;
    static final int EXPOSURE_PULSE_TICKS = 20;
    static final float MATTER_COST_PER_SECOND = 2.0f;

    public DarkmatterRadiation() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(0)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_PHASE_TUNING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Phase Tuning", "academy:darkmatter_phase_tuning"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputSystem.ANY_ACTION, 0
        );
        var binding = Client.CONFIG.getMaintainedKeyBinding(
                Client.KEY_NAME_CAST,
                defaultBinding,
                Client.LEGACY_KEY_NAME_CAST,
                Client.LEGACY_KEY_NAME_START,
                Client.LEGACY_KEY_NAME_END,
                Client.LEGACY_CANONICAL_START,
                Client.LEGACY_CANONICAL_END
        );
        var obsoleteDefault = InputSystem.combo(
                InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                InputSystem.ANY_ACTION, 0
        );
        if (binding.equals(obsoleteDefault)) {
            binding = defaultBinding;
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, binding);
        }
        AcademyCraftClient.Config.INSTANCE.save();
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST,
                binding,
                _ -> Client.start(),
                _ -> Client.stop(),
                _ -> Client.heartbeat(),
                () -> AbilitySystemClient.canUseSkill(Skills.DARKMATTER_RADIATION.get())
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_RADIATION.get(),
                        List.of(DarkmatterPhaseTuning.Client.SKILL_INFO),
                        R.textures.darkmatter_radiation_icon,
                        140,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_RADIATION + "_cast";
        private static final String LEGACY_KEY_NAME_CAST = "darkmatter_radiation_cast";
        private static final String LEGACY_KEY_NAME_START = "darkmatter_radiation_start";
        private static final String LEGACY_KEY_NAME_END = "darkmatter_radiation_end";
        private static final String LEGACY_CANONICAL_START = "darkmatter_interference_start";
        private static final String LEGACY_CANONICAL_END = "darkmatter_interference_end";
        public static Config CONFIG = new Config();
        private static boolean active;

        private Client() {
        }

        private static void start() {
            if (active || ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_RADIATION.get())) return;
            active = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            if (!active) return;
            active = false;
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        private static void heartbeat() {
            if (active) MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
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
        private static final Map<UUID, RadiationState> ACTIVE = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(StartPacket packet) {
            beginChannel(packet.getPacketListener().getPlayer());
        }

        public static boolean beginChannel(ServerPlayer player) {
            if (player == null) return false;
            var skill = Skills.DARKMATTER_RADIATION.get();
            if (!skill.isEnabled(player)) return false;
            var now = player.level().getGameTime();
            var existing = ACTIVE.get(player.getUUID());
            if (existing != null) {
                existing.leaseExpiresAt = now + RadiationState.LEASE_TICKS;
                return true;
            }
            if (ACTIVE.putIfAbsent(player.getUUID(), new RadiationState(
                    now, skill.getEffectiveProficiencyMilestone(player))) == null) {
                skill.reportTrigger(player);
                return true;
            }
            return false;
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            endChannel(packet.getPacketListener().getPlayer());
        }

        public static void endChannel(ServerPlayer player) {
            if (player != null) ACTIVE.remove(player.getUUID());
        }

        public static boolean isChanneling(ServerPlayer player) {
            return player != null && ACTIVE.containsKey(player.getUUID());
        }

        public static int getExposureTicks(ServerPlayer player, LivingEntity target) {
            if (player == null || target == null) return 0;
            var state = ACTIVE.get(player.getUUID());
            return state == null ? 0 : state.exposure.getOrDefault(target.getUUID(), 0);
        }

        static boolean insideFrontHemisphere(Vec3 eye, Vec3 look, Vec3 target) {
            return insideFrontHemisphere(eye, look, target, RANGE);
        }

        static boolean insideFrontHemisphere(Vec3 eye, Vec3 look, Vec3 target, double range) {
            return ViewTargetScanner.matches(
                    eye,
                    look,
                    range,
                    ViewTargetScanner.cone(range, 0.0),
                    new AABB(target, target)
            );
        }

        private static boolean isHostileTarget(ServerPlayer player, LivingEntity target) {
            return DarkmatterTargeting.isEnemyTarget(player, target);
        }

        private static void pulse(ServerLevel level, ServerPlayer player, RadiationState state) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle().normalize();
            var phase = DarkmatterPhase.weights(player);
            var alphaRange = alphaRange(phase.alpha(), state.milestone);
            var betaRange = betaRange(phase.beta(), state.milestone);
            var sixMilestone = Skills.DARKMATTER_SIX_WINGS.get()
                    .getEffectiveProficiencyMilestone(player);
            if (phase.gamma() > 0.0f) {
                var area = DarkmatterSixWings.Server.areaMultiplier(sixMilestone);
                alphaRange *= area;
                betaRange *= area;
            }
            var queryRange = Math.max(alphaRange, betaRange);
            var alphaMinimumDot = Math.cos(Math.toRadians(alphaHalfAngle(phase.alpha())));
            var betaMinimumDot = Math.cos(Math.toRadians(betaHalfAngle(phase.beta())));
            spawnRadiationVisual(level, player, eye, look, queryRange);
            var alphaArea = ViewTargetScanner.cone(alphaRange, alphaMinimumDot);
            var betaArea = ViewTargetScanner.cone(betaRange, betaMinimumDot);
            var phaseArea = ViewTargetScanner.union(alphaArea, betaArea);
            var targets = ViewTargetScanner.scan(
                    level,
                    LivingEntity.class,
                    eye,
                    look,
                    queryRange,
                    phaseArea,
                    target -> isHostileTarget(player, target)
            );
            if (targets.isEmpty()) {
                state.exposure.clear();
                return;
            }

            var skill = Skills.DARKMATTER_RADIATION.get();
            var system = AbilitySystemServer.getSystem(player);
            var power = system.getPlayerAbilityPowerMultiplier(player.getUUID());
            var damageMultiplier = system.getPlayerDamageMultiplier(player.getUUID());
            var gammaMagnitude = DarkmatterSixWings.Server.gammaMagnitudeMultiplier(player);
            var darkmatterSource = SkillDamageSource.of(player, skill);
            var alphaTargets = new ArrayList<LivingEntity>();
            var betaExposed = new HashSet<UUID>();
            for (var target : targets) {
                var center = target.getBoundingBox().getCenter();
                var targetBounds = target.getBoundingBox();
                var inAlpha = ViewTargetScanner.matches(
                        eye, look, queryRange, alphaArea, targetBounds);
                var inBeta = ViewTargetScanner.matches(
                        eye, look, queryRange, betaArea, targetBounds);
                if (!inAlpha && !inBeta) continue;
                target.invulnerableTime = 0;
                var hit = false;
                if (inAlpha) {
                    hit = DarkmatterTargeting.hurt(level, target, darkmatterSource,
                            alphaPulseDamage(phase.alpha()) * power * damageMultiplier);
                    var push = center.subtract(eye);
                    if (push.lengthSqr() > 1.0e-6) {
                        push = push.normalize().scale(0.05f + 0.04f * phase.alpha());
                        target.push(push.x, Math.max(0.02, push.y), push.z);
                    }
                }
                if (inBeta && target.isAlive()) {
                    target.invulnerableTime = 0;
                    hit |= DarkmatterTargeting.hurt(
                            level,
                            target,
                            darkmatterSource,
                            betaPulseDamage(phase.beta()) * power * damageMultiplier
                    );
                }
                if (!hit) continue;
                if (inAlpha) alphaTargets.add(target);
                var detonation = DarkmatterLawMark.detonate(player, target);
                if (detonation > 0.0f) {
                    target.invulnerableTime = 0;
                    SkillDamageUtil.applyDirect(level, target, darkmatterSource, detonation);
                    target.invulnerableTime = 0;
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
                }
                if (inBeta && phase.beta() > 0.0f) {
                    betaExposed.add(target.getUUID());
                    target.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING, 40 + Math.round(phase.beta() * 20), 0));
                    target.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS, 40 + Math.round(phase.beta() * 20), 0));
                    var exposure = state.exposure.merge(
                            target.getUUID(), pulseInterval(state.milestone), Integer::sum);
                    if (exposure >= exposurePulseTicks(state.milestone)) {
                        state.exposure.put(target.getUUID(), 0);
                        target.invulnerableTime = 0;
                        DarkmatterTargeting.hurt(level, target, darkmatterSource,
                                exposureBurstDamage(phase.beta()));
                    }
                }
                level.sendParticles(ParticleTypes.PORTAL,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        6, 0.2, 0.25, 0.2, 0.02);
            }
            state.exposure.keySet().retainAll(betaExposed);

            var now = level.getGameTime();
            if (phase.alpha() > 0.0f && !alphaTargets.isEmpty()
                    && now >= state.nextAlphaFeatherTick) {
                spawnFeathers(
                        level,
                        player,
                        alphaTargets,
                        alphaFeatherCount(phase.alpha()),
                        alphaFeatherDamage(phase.alpha()) * power * damageMultiplier,
                        0.0f,
                        alphaRange);
                state.nextAlphaFeatherTick = now + 20;
            }

            if (phase.gamma() > 0.0f && now >= state.nextGammaTick) {
                gammaFeatherPulse(level, player, targets, phase.gamma(), phase.beta(),
                        state.milestone, power,
                        damageMultiplier * gammaMagnitude);
                state.nextGammaTick = now + 20;
            }
        }

        static double alphaRange(float alpha, int milestone) {
            var range = 12.0 + Math.max(0.0f, alpha) * 2.0;
            return Math.clamp(milestone, 0, 3) >= 2 ? range * 1.15 : range;
        }

        static double betaRange(float beta, int milestone) {
            var range = 10.0 + Math.max(0.0f, beta) * 2.0;
            return Math.clamp(milestone, 0, 3) >= 2 ? range * 1.15 : range;
        }

        static double alphaHalfAngle(float alpha) {
            return Math.max(12.0, 32.0 - Math.max(0.0f, alpha) * 3.0);
        }

        static double betaHalfAngle(float beta) {
            return Math.min(80.0, 40.0 + Math.max(0.0f, beta) * 8.0);
        }

        static float alphaPulseDamage(float alpha) {
            return 0.5f + 0.4f * Math.max(0.0f, alpha);
        }

        static float betaPulseDamage(float beta) {
            return 0.25f + 0.25f * Math.max(0.0f, beta);
        }

        static float exposureBurstDamage(float beta) {
            return 1.0f + Math.max(0.0f, beta);
        }

        static int exposurePulseTicks(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 3 ? 15 : EXPOSURE_PULSE_TICKS;
        }

        static int pulseInterval(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 2 ? 4 : 5;
        }

        static int gammaFeatherCount(float gamma, int milestone) {
            if (!(gamma > 0.0f)) return 0;
            return 2 + Math.round(gamma) + (Math.clamp(milestone, 0, 3) >= 3 ? 2 : 0);
        }

        static int alphaFeatherCount(float alpha) {
            return Math.max(0, 1 + (int) Math.floor(Math.max(0.0f, alpha) / 2.0f));
        }

        static float alphaFeatherDamage(float alpha) {
            return 0.75f + 0.35f * Math.max(0.0f, alpha);
        }

        static float maintenanceCost(int milestone) {
            return MATTER_COST_PER_SECOND
                    * (Math.clamp(milestone, 0, 3) >= 1 ? 0.9f : 1.0f);
        }

        private static void gammaFeatherPulse(
                ServerLevel level, ServerPlayer player, List<LivingEntity> candidates,
                float gamma, float beta, int milestone,
                float power, float damageMultiplier
        ) {
            var count = gammaFeatherCount(gamma, milestone);
            var radius = 4.0 + 2.0 * gamma;
            var nearby = candidates.stream()
                    .filter(target -> target.distanceToSqr(player) <= radius * radius)
                    .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(player)))
                    .toList();
            spawnFeathers(
                    level,
                    player,
                    nearby,
                    count,
                    (0.5f + 0.2f * gamma) * power * damageMultiplier,
                    exposureBurstDamage(beta) * power * damageMultiplier,
                    radius);
        }

        private static void spawnFeathers(
                ServerLevel level,
                ServerPlayer player,
                List<LivingEntity> candidates,
                int count,
                float damage,
                float exposureBurstDamage,
                double range
        ) {
            if (count <= 0) return;
            var targets = candidates.stream()
                    .filter(target -> isHostileTarget(player, target)
                            && target.distanceToSqr(player) <= range * range)
                    .sorted(Comparator.comparingDouble(
                            target -> target.distanceToSqr(player)))
                    .toList();
            for (var index = 0; index < count; index++) {
                var target = targets.isEmpty() ? null : targets.get(index % targets.size());
                var angle = Math.PI * 2.0 * index / Math.max(1, count);
                var direction = target == null
                        ? new Vec3(Math.cos(angle), 0.05, Math.sin(angle))
                        : target.getBoundingBox().getCenter().subtract(player.getEyePosition());
                var feather = new DarkmatterFeatherProjectile(
                        EntityTypes.DARKMATTER_FEATHER_PROJECTILE.get(), level);
                feather.configure(player, target, direction, damage, exposureBurstDamage);
                level.addFreshEntity(feather);
            }
        }

        private static void spawnRadiationVisual(ServerLevel level, ServerPlayer player,
                                                 Vec3 eye, Vec3 look, double range) {
            var right = look.cross(new Vec3(0.0, 1.0, 0.0));
            if (right.lengthSqr() < 1.0E-8) right = new Vec3(1.0, 0.0, 0.0);
            right = right.normalize();
            var up = right.cross(look).normalize();
            for (var index = 0; index < 20; index++) {
                var distance = 2.0 + player.getRandom().nextDouble() * (range - 2.0);
                var spread = distance * 0.28;
                var point = eye.add(look.scale(distance))
                        .add(right.scale((player.getRandom().nextDouble() - 0.5) * spread))
                        .add(up.scale((player.getRandom().nextDouble() - 0.5) * spread));
                level.sendParticles(
                        index % 3 == 0 ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL,
                        point.x, point.y, point.z,
                        1, 0.02, 0.02, 0.02, 0.0
                );
            }
        }

        public static void tick(ServerPlayer player) {
            var state = ACTIVE.get(player.getUUID());
            if (state == null) return;
            var skill = Skills.DARKMATTER_RADIATION.get();
            if (!player.isAlive() || player.hasDisconnected() || !skill.isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) {
                ACTIVE.remove(player.getUUID());
                return;
            }
            var now = level.getGameTime();
            if (now > state.leaseExpiresAt) {
                ACTIVE.remove(player.getUUID(), state);
                return;
            }
            skill.reportActivity(player, false);
            if (now >= state.nextCostTick) {
                var manager = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
                var paid = new boolean[1];
                var executed = skill.executeContinuous(player, _ -> 0.0f, (context, _) -> {
                    paid[0] = manager.consume(
                            player,
                            maintenanceCost(context.milestone()),
                            skill,
                            skill.getIterationTicks(player));
                }, true);
                if (!executed || !paid[0]) {
                    ACTIVE.remove(player.getUUID());
                    return;
                }
                skill.reportActivity(player, true);
                state.nextCostTick = now + 20;
            }
            if (now < state.nextDamageTick) return;
            pulse(level, player, state);
            state.nextDamageTick = now + pulseInterval(state.milestone);
        }

        private static final class RadiationState {
            private static final long LEASE_TICKS = 60;
            private final int milestone;
            private final Map<UUID, Integer> exposure = new HashMap<>();
            private long nextDamageTick;
            private long nextCostTick;
            private long nextAlphaFeatherTick;
            private long nextGammaTick;
            private long leaseExpiresAt;

            private RadiationState(long now, int milestone) {
                this.milestone = milestone;
                nextDamageTick = now;
                nextCostTick = now;
                nextAlphaFeatherTick = now;
                nextGammaTick = now;
                leaseExpiresAt = now + LEASE_TICKS;
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.endChannel(player);
        }

        @SubscribeEvent
        public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.endChannel(player);
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
            return PacketTypes.DARKMATTER_RADIATION_START.get();
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
            return PacketTypes.DARKMATTER_RADIATION_STOP.get();
        }
    }
}
