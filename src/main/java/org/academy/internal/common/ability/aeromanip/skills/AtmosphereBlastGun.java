package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AtmosphereBlastGun extends Skill {
    static final double LENGTH = 8.0;
    static final double HALF_WIDTH = 1.0;
    private static final double KNOCKBACK_STRENGTH = 1.8;
    private static final double KNOCKBACK_UP = 0.45;

    public AtmosphereBlastGun() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(40)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.ATMOSPHERE_SHIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
        );
    }

    static boolean isInsideBlastVolume(Vec3 eye, Vec3 look, Vec3 target, double targetRadius) {
        return isInsideBlastVolume(eye, look, target, targetRadius, LENGTH, HALF_WIDTH);
    }

    static boolean isInsideBlastVolume(Vec3 eye, Vec3 look, Vec3 target, double targetRadius, double length, double halfWidth) {
        if (look.lengthSqr() <= 1.0e-6) return false;
        var direction = look.normalize();
        var toTarget = target.subtract(eye);
        var forward = toTarget.dot(direction);
        if (forward < 0 || forward > length) return false;
        var lateral = toTarget.subtract(direction.scale(forward)).length();
        return lateral <= halfWidth + Math.max(0, targetRadius);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_START,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_START,
                        InputSystem.combo(
                                InputSystem.InputType.MOUSE,
                                InputConstants.MOUSE_BUTTON_LEFT,
                                InputConstants.PRESS,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.start()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_STOP,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_STOP,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT,
                                InputConstants.RELEASE, InputConstants.MOD_ALT)
                ),
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
                        Skills.ATMOSPHERE_BLAST_GUN.get(),
                        List.of(AtmosphereShield.Client.SKILL_INFO),
                        R.textures.atmosphere_blast_gun_icon,
                        20,
                        104
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.ATMOSPHERE_BLAST_GUN + "_cast";
        public static final String KEY_NAME_START = SkillNames.ATMOSPHERE_BLAST_GUN + "_start";
        public static final String KEY_NAME_STOP = SkillNames.ATMOSPHERE_BLAST_GUN + "_stop";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (!AbilitySystemClient.canUseSkill(Skills.ATMOSPHERE_BLAST_GUN.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.ATMOSPHERE_BLAST_GUN.get()))
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
        private static final Map<ServerPlayer, Long> CHARGING = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            tryAutomatedAttack(packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (Skills.ATMOSPHERE_BLAST_GUN.get().isEnabled(player) && !CHARGING.containsKey(player)) {
                CHARGING.put(player, player.level().getGameTime());
            }
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var start = CHARGING.remove(player);
            fire(player, start == null ? 0 : (int) Math.min(20, Math.max(0, player.level().getGameTime() - start)));
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            return fire(player, 0);
        }

        private static boolean fire(ServerPlayer player, int chargeTicks) {
            if (!(player.level() instanceof ServerLevel level)) return false;
            var skill = Skills.ATMOSPHERE_BLAST_GUN.get();
            var focused = chargeTicks > 0;
            return skill.executeActive(player, context -> (focused ? 60.0f : 40.0f)
                    * AeromanipConfig.cpMultiplier(player, SkillNames.ATMOSPHERE_BLAST_GUN), (context, _) -> {
                var eye = player.getEyePosition();
                var look = player.getLookAngle();
                if (look.lengthSqr() <= 1.0e-6) return;

                var length = (focused ? 20.0 : LENGTH)
                        * AeromanipConfig.rangeMultiplier(player, SkillNames.ATMOSPHERE_BLAST_GUN);
                if (context.milestone() >= 2) {
                    length = focused ? length * 1.25 : 10.0
                            * AeromanipConfig.rangeMultiplier(player, SkillNames.ATMOSPHERE_BLAST_GUN);
                }
                var width = focused ? 0.5 : HALF_WIDTH;
                var resolvedLength = length;
                var damageBase = (focused ? 10.0f : 6.0f)
                        * AeromanipConfig.damageMultiplier(player, SkillNames.ATMOSPHERE_BLAST_GUN);
                var searchBox = new AABB(eye, eye).inflate(length);
                var targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        searchBox,
                        target -> target != player
                                && target.isAlive()
                                && !target.isSpectator()
                                && (MentalControlRuntime
                                .canForceAttack(player, target)
                                || AeromanipTargeting.canAffectNegatively(player, target))
                                && player.hasLineOfSight(target)
                                && isInsideBlastVolume(
                                eye,
                                look,
                                target.getBoundingBox().getCenter(),
                                target.getBbWidth() * 0.5,
                                resolvedLength,
                                width
                        )
                );

                var damage = damageBase
                        * context.system().getPlayerAbilityPowerMultiplier(player.getUUID())
                        * context.system().getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, Skills.ATMOSPHERE_BLAST_GUN.get());
                level.playSound(null, player.blockPosition(),
                        SoundEvents.AIRFLOW_IMPACT.get(),
                        SoundSource.PLAYERS, 0.8f, focused ? 0.75f : 1.0f);
                var direction = look.normalize();
                for (var target : targets) {
                    if (target.hurtServer(level, source, damage)) {
                        applyKnockback(target, direction, source, damage);
                    }
                }
                if (focused && context.milestone() >= 3 && !targets.isEmpty()) {
                    var first = targets.stream().min(java.util.Comparator.comparingDouble(target ->
                            target.getBoundingBox().getCenter().subtract(eye).dot(direction))).orElse(null);
                    if (first != null) applyFocusedAftershock(player, level, first, targets, direction,
                            source, damage * 0.4f);
                }
            });
        }

        private static void applyFocusedAftershock(ServerPlayer owner, ServerLevel level, LivingEntity first,
                                                   List<? extends LivingEntity> primaryTargets, Vec3 direction,
                                                   DamageSource source, float damage) {
            var start = first.getBoundingBox().getCenter().add(direction.scale(first.getBbWidth() * 0.5));
            var box = new AABB(start, start.add(direction.scale(4.0))).inflate(3.0);
            for (var target : level.getEntitiesOfClass(LivingEntity.class, box,
                    target -> target != owner && target != first && target.isAlive()
                            && AeromanipTargeting.canAffectNegatively(owner, target))) {
                var relative = target.getBoundingBox().getCenter().subtract(start);
                var forward = relative.dot(direction);
                if (forward < 0.0 || forward > 4.0) continue;
                var allowed = 0.5 + forward * 0.5;
                if (relative.subtract(direction.scale(forward)).lengthSqr() > allowed * allowed) continue;
                if (target.hurtServer(level, source, damage)) applyKnockback(target, direction, source, damage);
            }
        }

        private static void applyKnockback(
                LivingEntity target,
                Vec3 look,
                DamageSource source,
                float damage
        ) {
            if (AeromanipTargeting.isBoss(target)) return;
            if (!(source.getEntity() instanceof ServerPlayer owner)) return;
            var force = AeromanipTargeting.forceMultiplier(owner, target);
            if (force <= 0.0) return;
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() <= 1.0e-6) {
                horizontal = look;
            }
            horizontal = horizontal.normalize();
            target.knockback(KNOCKBACK_STRENGTH * force, -horizontal.x, -horizontal.z, source, damage);
            var movement = target.getDeltaMovement();
            var y = Math.max(movement.y, KNOCKBACK_UP);
            if (Double.isFinite(movement.x) && Double.isFinite(y) && Double.isFinite(movement.z)) {
                var velocity = new Vec3(movement.x, y, movement.z);
                if (velocity.lengthSqr() > 9.0) velocity = velocity.normalize().scale(3.0);
                AeromanipTargeting.addClampedVelocity(target, velocity.subtract(movement));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.ATMOSPHERE_BLAST_GUN_CAST.get();
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
            return PacketTypes.ATMOSPHERE_BLAST_GUN_START.get();
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
            return PacketTypes.ATMOSPHERE_BLAST_GUN_STOP.get();
        }
    }
}
