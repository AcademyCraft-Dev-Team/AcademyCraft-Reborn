package org.academy.internal.common.ability.mentalout.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlHandle;
import org.academy.api.common.entitycontrol.ControlRequest;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.ability.mentalout.skills.lv2.SensoryDistortion;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A ten-second, single-target mental damage-over-time attack with an independently blockable stupor. */
public final class MindDestruction extends Skill {
    public static final double RANGE = 64.0;
    public static final int DURATION_TICKS = 200;
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    public static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;
    public static final float BASE_DAMAGE = 10.0f;
    private static final int STUPOR_PRIORITY = 250;
    private static final Map<EffectKey, ActiveEffect> ACTIVE = new HashMap<>();

    public MindDestruction() {
        super(Builder
                .of(AbilityCategories.MENTALOUT.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(100)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.SENSORY_DISTORTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Sensory Distortion", "academy:sensory_distortion"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_USE,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_G,
                        InputConstants.PRESS,
                        0
                )
        ), _ -> Client.use());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static float damagePerPulse(float maximumHealth) {
        return Math.max(0.0f, maximumHealth) * MAX_HEALTH_DAMAGE_RATIO + BASE_DAMAGE;
    }

    static boolean shouldApplyStupor(boolean targetInRoster) {
        return targetInRoster;
    }

    public static void tick(MinecraftServer server) {
        var now = server.overworld().getGameTime();
        for (var effect : List.copyOf(ACTIVE.values())) {
            var controller = server.getPlayerList().getPlayer(effect.key.controllerId);
            var target = findLivingEntity(server, effect.key.targetId);
            if (controller == null || target == null || !target.isAlive() || target.isRemoved()
                    || controller.level() != target.level() || now > effect.expiresAt) {
                close(effect);
                continue;
            }
            if (target instanceof ServerPlayer subject
                    && effect.stupor != null && !effect.stupor.isClosed()) {
                MentalResistanceManager.markAffected(controller, subject, false);
            }
            if (now < effect.nextDamageTick || effect.pulsesRemaining <= 0) continue;
            effect.nextDamageTick += DAMAGE_INTERVAL_TICKS;
            effect.pulsesRemaining--;
            SkillDamageUtil.apply(
                    controller,
                    target,
                    Skills.MIND_DESTRUCTION.get(),
                    DamageTypes.MENTAL_DAMAGE,
                    damagePerPulse(target.getMaxHealth())
            );
            if (target.level() instanceof ServerLevel level) {
                var center = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.SOUL,
                        center.x, center.y, center.z,
                        8, target.getBbWidth() * 0.35, target.getBbHeight() * 0.25,
                        target.getBbWidth() * 0.35, 0.02);
            }
            if (effect.pulsesRemaining <= 0) close(effect);
        }
    }

    public static void releaseEntity(UUID entityId) {
        if (entityId == null) return;
        for (var effect : List.copyOf(ACTIVE.values())) {
            if (effect.key.controllerId.equals(entityId) || effect.key.targetId.equals(entityId)) {
                close(effect);
            }
        }
    }

    public static void clear() {
        List.copyOf(ACTIVE.values()).forEach(MindDestruction::close);
        ACTIVE.clear();
    }

    private static void start(ServerPlayer controller, LivingEntity target, boolean applyStupor) {
        var now = controller.level().getGameTime();
        var key = new EffectKey(controller.getUUID(), target.getUUID());
        var previous = ACTIVE.remove(key);
        if (previous != null) close(previous);
        ControlHandle stupor = null;
        if (applyStupor && !MentalControlRuntime.isProtectedTarget(target)) {
            try {
                stupor = MentalControlApi.apply(new ControlRequest(
                        controller,
                        target,
                        Skills.MIND_DESTRUCTION.get().getKey(),
                        STUPOR_PRIORITY,
                        now + DURATION_TICKS,
                        List.of(new ControlDirective.FreezeAi())
                ));
            } catch (RuntimeException ignored) {
                // Mental protection only suppresses stupor; the damage-over-time still starts.
            }
        } else if (applyStupor) {
            MentalControlRuntime.notifyProtectionBlocked(controller, target);
        }
        var effect = new ActiveEffect(
                key,
                now + DURATION_TICKS,
                now + DAMAGE_INTERVAL_TICKS,
                DURATION_TICKS / DAMAGE_INTERVAL_TICKS,
                stupor
        );
        ACTIVE.put(key, effect);
        controller.level().playSound(null, target.blockPosition(),
                SoundEvents.SENSORY_DISTORTION.get(), SoundSource.PLAYERS, 0.9f, 0.65f);
    }

    private static void close(ActiveEffect effect) {
        if (effect == null) return;
        ACTIVE.remove(effect.key, effect);
        if (effect.stupor != null) effect.stupor.close();
    }

    private static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MENTALOUT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MIND_DESTRUCTION.get(),
                        List.of(SensoryDistortion.Client.SKILL_INFO),
                        R.textures.ability.mentalout.skill.sensory_distortion.icon,
                        200,
                        100
                )
        );
        public static final String KEY_NAME_USE = SkillNames.MIND_DESTRUCTION + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.MIND_DESTRUCTION.get())) return;
            MisakaNetworkClient.send(new UsePacket(MentaloutRequestGuard.nextClientSequence()));
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
        private Server() {
        }

        @SubscribePacket
        public static void use(UsePacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(),
                    MentaloutRequestGuard.SkillUse.MIND_DESTRUCTION,
                    packet.sequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.MIND_DESTRUCTION.get();
            if (!skill.isEnabled(player)) {
                feedback(player, "message.academy.mentalout.skill_unavailable");
                return;
            }
            var target = MentaloutTargeting.findLookedAtLiving(player, RANGE);
            if (target == null || FriendlyFireSetting.shouldPrevent(player, target)) {
                feedback(player, "message.academy.mentalout.invalid_target");
                return;
            }
            var roster = MentaloutControlContext.get(player);
            var applyStupor = shouldApplyStupor(
                    roster != null && roster.contains(target.getUUID()));
            if (!skill.executeActive(player, (_, _) -> start(player, target, applyStupor))) {
                feedback(player, "message.academy.mentalout.insufficient_cp");
            }
        }

        private static void feedback(ServerPlayer player, String key) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = ByteBufCodecs.LONG.map(
                UsePacket::new,
                packet -> packet.sequence
        );
        private final long sequence;

        public UsePacket(long sequence) {
            this.sequence = sequence;
        }

        public long sequence() {
            return sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.MIND_DESTRUCTION_USE.get();
        }
    }

    private record EffectKey(UUID controllerId, UUID targetId) {
    }

    private static final class ActiveEffect {
        private final EffectKey key;
        private final long expiresAt;
        private long nextDamageTick;
        private int pulsesRemaining;
        private final ControlHandle stupor;

        private ActiveEffect(
                EffectKey key,
                long expiresAt,
                long nextDamageTick,
                int pulsesRemaining,
                ControlHandle stupor
        ) {
            this.key = key;
            this.expiresAt = expiresAt;
            this.nextDamageTick = nextDamageTick;
            this.pulsesRemaining = pulsesRemaining;
            this.stupor = stupor;
        }
    }
}
