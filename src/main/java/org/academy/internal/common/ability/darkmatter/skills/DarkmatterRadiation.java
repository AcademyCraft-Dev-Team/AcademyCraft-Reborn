package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

public final class DarkmatterRadiation extends Skill {
    static final double RANGE = 32.0;
    static final int PULSE_INTERVAL = 2;
    static final float FLAT_DAMAGE = 2.0f;
    static final float MIN_DARKMATTER_DAMAGE = 2.0f;
    static final float HEALTH_FRACTION = 0.001f;

    public DarkmatterRadiation() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(10)
                .iterationTicks(40)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_CUT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Cut", "academy:darkmatter_cut"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                        InputConstants.PRESS, 0)
        ), context -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                        InputConstants.RELEASE, 0)
        ), context -> Client.stop());
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
                        List.of(DarkmatterCut.Client.SKILL_INFO),
                        R.textures.darkmatter_radiation_icon,
                        75,
                        104
                )
        );
        public static final String KEY_NAME_START = SkillNames.DARKMATTER_RADIATION + "_start";
        public static final String KEY_NAME_END = SkillNames.DARKMATTER_RADIATION + "_end";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_RADIATION.get())) return;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {
                }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final Map<UUID, RadiationState> ACTIVE = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DARKMATTER_RADIATION.get();
            if (!skill.isEnabled(player)) return;
            if (ACTIVE.putIfAbsent(player.getUUID(), new RadiationState(
                    player.level().getGameTime(), skill.getEffectiveProficiencyMilestone(player))) == null) {
                skill.reportTrigger(player);
            }
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            ACTIVE.remove(packet.getPacketListener().getPlayer().getUUID());
        }

        static float damage(float maxHealth, float abilityPower) {
            return FLAT_DAMAGE + darkmatterDamage(maxHealth, abilityPower);
        }

        static float darkmatterDamage(float maxHealth, float abilityPower) {
            return Math.max(MIN_DARKMATTER_DAMAGE, maxHealth * HEALTH_FRACTION) * abilityPower;
        }

        static boolean insideFrontHemisphere(Vec3 eye, Vec3 look, Vec3 target) {
            return insideFrontHemisphere(eye, look, target, RANGE);
        }

        static boolean insideFrontHemisphere(Vec3 eye, Vec3 look, Vec3 target, double range) {
            var offset = target.subtract(eye);
            return offset.lengthSqr() <= range * range
                    && (offset.lengthSqr() <= 1.0e-6 || look.dot(offset.normalize()) >= 0);
        }

        private static boolean isHostileTarget(ServerPlayer player, LivingEntity target) {
            if (target == player || target instanceof Player || !target.isAlive()
                    || target.isRemoved() || player.isAlliedTo(target)) return false;
            if (target instanceof TamableAnimal tame && tame.isOwnedBy(player)) return false;
            return target instanceof Enemy
                    || target instanceof Mob mob && mob.getTarget() == player;
        }

        private static void pulse(ServerLevel level, ServerPlayer player, RadiationState state) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle().normalize();
            var range = state.milestone >= 2 ? 36.0 : RANGE;
            spawnRadiationVisual(level, player, eye, look, range);
            var targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(eye, eye).inflate(range),
                    target -> isHostileTarget(player, target)
                            && insideFrontHemisphere(eye, look,
                            target.getBoundingBox().getCenter(), range));
            if (targets.isEmpty()) return;

            var skill = Skills.DARKMATTER_RADIATION.get();
            var power = AbilitySystemServer.getSystem(player)
                    .getPlayerAbilityPowerMultiplier(player.getUUID());
            var darkmatterSource = SkillDamageSource.of(player, skill);
            for (var target : targets) {
                target.invulnerableTime = 0;
                var hit = target.hurtServer(level, darkmatterSource, FLAT_DAMAGE);
                if (target.isAlive()) {
                    target.invulnerableTime = 0;
                    hit |= target.hurtServer(
                            level,
                            darkmatterSource,
                            darkmatterDamage(target.getMaxHealth(), power)
                    );
                }
                if (!hit) continue;
                if (state.milestone >= 3) {
                    var exposure = state.exposure.merge(target.getUUID(), 1, Integer::sum);
                    if (exposure >= 20) {
                        state.exposure.put(target.getUUID(), 0);
                        radiationPulse(level, player, target, skill, power);
                    }
                }
                level.sendParticles(ParticleTypes.PORTAL,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        6, 0.2, 0.25, 0.2, 0.02);
            }
        }

        private static void radiationPulse(ServerLevel level, ServerPlayer player, LivingEntity center,
                                           Skill skill, float power) {
            var source = SkillDamageSource.of(player, skill);
            var processed = 0;
            for (var target : level.getEntitiesOfClass(LivingEntity.class,
                    center.getBoundingBox().inflate(3.0), target -> isHostileTarget(player, target))) {
                if (processed++ >= 96) break;
                target.invulnerableTime = 0;
                target.hurtServer(level, source, damage(target.getMaxHealth(), power) * 4.0f);
            }
            level.sendParticles(ParticleTypes.WITCH, center.getX(), center.getY() + center.getBbHeight() * 0.5,
                    center.getZ(), 24, 1.5, 1.5, 1.5, 0.08);
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

        private static void tick(ServerPlayer player) {
            var state = ACTIVE.get(player.getUUID());
            if (state == null) return;
            var skill = Skills.DARKMATTER_RADIATION.get();
            if (!player.isAlive() || player.hasDisconnected() || !skill.isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) {
                ACTIVE.remove(player.getUUID());
                return;
            }
            var now = level.getGameTime();
            skill.reportActivity(player, false);
            if (now >= state.nextCostTick) {
                if (!skill.executeContinuous(player, (context, actualCost) -> {
                }, false)) {
                    ACTIVE.remove(player.getUUID());
                    return;
                }
                state.nextCostTick = now + PULSE_INTERVAL;
            }
            if (now < state.nextDamageTick) return;
            pulse(level, player, state);
            state.nextDamageTick = now + 1;
        }

        private static final class RadiationState {
            private final int milestone;
            private final Map<UUID, Integer> exposure = new HashMap<>();
            private long nextDamageTick;
            private long nextCostTick;
            private int cursor;

            private RadiationState(long now, int milestone) {
                this.milestone = milestone;
                nextDamageTick = now;
                nextCostTick = now;
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
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);
        private StartPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.DARKMATTER_RADIATION_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);
        private StopPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.DARKMATTER_RADIATION_STOP.get();
        }
    }
}
