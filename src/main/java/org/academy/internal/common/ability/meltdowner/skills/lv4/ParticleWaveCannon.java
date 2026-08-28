package org.academy.internal.common.ability.meltdowner.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.accelerator.reflection.ResolvedLinearAttack;
import org.academy.internal.common.ability.meltdowner.ContinuousBeamReflection;
import org.academy.internal.common.ability.meltdowner.ContinuousReflectionSession;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamActions;
import org.academy.internal.common.ability.meltdowner.skills.ContinuousBeam;
import org.academy.internal.common.ability.meltdowner.skills.lv1.RadiationIntensify;
import org.academy.internal.common.ability.meltdowner.skills.lv2.ScatterBomb;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public final class ParticleWaveCannon extends Skill {
    static final int CHARGE_TICKS = 25;
    static final int CP_INTERVAL_TICKS = 2;
    static final int DAMAGE_INTERVAL_TICKS = 10;
    static final float MAX_LENGTH = 85.0f;
    static final float DAMAGE_RADIUS = 1.2f;
    static final float BASE_DAMAGE = 40.0f;
    static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;
    static final float BREAK_RADIUS = 0.6f;
    static final int MINING_TIER = 4;

    public ParticleWaveCannon() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .damage()
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(10)
                .iterationTicks(15)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SCATTER_BOMB)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Scatter Bomb",
                        "academy:scatter_bomb"
                ))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, 0)
        ), ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_STOP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, 0)
        ), ctx -> Client.stop());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.PARTICLE_WAVE_CANNON.get(),
                        List.of(ScatterBomb.Client.SKILL_INFO),
                        R.textures.particle_wave_cannon_icon,
                        115,
                        65
                )
        );
        public static final String KEY_NAME_START = SkillNames.PARTICLE_WAVE_CANNON + "_start";
        public static final String KEY_NAME_STOP = SkillNames.PARTICLE_WAVE_CANNON + "_stop";
        public static Config CONFIG = new Config();

        private static void start() {
            if (!AbilitySystemClient.canUseSkill(Skills.PARTICLE_WAVE_CANNON.get())) return;
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.PARTICLE_WAVE_CANNON.get();
            if (!skill.isEnabled(player) || CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CONTEXT_MAP.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }
    }

    public static final class Context extends ServerContext {
        private final ServerLevel initialLevel;
        private int ticks;
        private int lastPaidTick;
        private boolean beaming;
        private boolean ended;
        private HighSpeedElectronBeam visual;
        private final int proficiencyMilestone;
        private final float maximumLength;
        private final float damageRadius;
        private final float breakRadius;
        private final Map<UUID, LivingEntity> beamTargets = new HashMap<>();
        private final ContinuousReflectionSession reflectionSession = new ContinuousReflectionSession();

        private Context(ServerPlayer player) {
            super(player);
            initialLevel = player.level();
            proficiencyMilestone = Skills.PARTICLE_WAVE_CANNON.get().getEffectiveProficiencyMilestone(player);
            maximumLength = proficiencyMilestone >= 2 ? 96.0f : MAX_LENGTH;
            damageRadius = proficiencyMilestone >= 2 ? DAMAGE_RADIUS * 1.15f : DAMAGE_RADIUS;
            breakRadius = proficiencyMilestone >= 2 ? BREAK_RADIUS * 1.15f : BREAK_RADIUS;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.PARTICLE_WAVE_CANNON.get();
            if (player.level() != initialLevel
                    || !skill.isEnabled(player)
                    || !player.isAlive()
                    || player.hasDisconnected()) {
                end();
                return;
            }
            ticks++;
            skill.reportActivity(player, false);

            if (!beaming && ticks >= CHARGE_TICKS) {
                if (!skill.executeActive(player, (_, _) -> beginBeam())) {
                    end();
                    return;
                }
                lastPaidTick = ticks;
            }
            if (!beaming) return;

            if (!ContinuousBeam.followFromMainHand(player, visual, maximumLength, 0.2f)) {
                end();
                return;
            }

            if (ticks - lastPaidTick >= CP_INTERVAL_TICKS) {
                if (!skill.executeContinuous(player, (_, _) -> {
                }, false)) {
                    end();
                    return;
                }
                lastPaidTick = ticks;
            }

            var start = visual.position();
            var end = start.add(player.getLookAngle().scale(maximumLength));
            var damageTick = ticks % DAMAGE_INTERVAL_TICKS == 0;
            var system = AbilitySystemServer.getSystem(player);
            var payload = MeltdownerBeamActions.createPayload(
                    initialLevel,
                    player,
                    skill,
                    damageRadius,
                    BASE_DAMAGE,
                    MAX_HEALTH_DAMAGE_RATIO,
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID()),
                    Skills.RADIATION_INTENSIFY.get().isEnabled(player)
            );
            var attack = ContinuousBeamReflection.resolve(
                    initialLevel,
                    new LinearSegment(start, end),
                    payload,
                    reflectionSession,
                    ticks,
                    DAMAGE_INTERVAL_TICKS,
                    damageTick
            );
            updateVisual(attack);

            if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.PARTICLE_WAVE_CANNON.get())) {
                skill.reportActivity(player, true);
                MeltdownerBeamActions.destroyBlocksAlongSegment(
                        initialLevel,
                        attack.outbound(),
                        breakRadius,
                        MINING_TIER,
                        false,
                        true,
                        true,
                        player
                );
            }
            LinearAttackExecutor.SegmentExecutionResult outboundResult = null;
            if (damageTick) {
                outboundResult = LinearAttackExecutor.executeOutbound(initialLevel, attack, payload);
            }
            if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.PARTICLE_WAVE_CANNON.get())) {
                attack.returnSegment().ifPresent(segment -> MeltdownerBeamActions.destroyBlocksAlongSegment(
                        initialLevel,
                        segment,
                        breakRadius,
                        MINING_TIER,
                        false,
                        true,
                        true,
                        attack.reflectionCandidate().orElseThrow().reflector()
                ));
            }
            if (damageTick) {
                var returnResult = LinearAttackExecutor.executeReturn(initialLevel, attack, payload, outboundResult);
                if (proficiencyMilestone >= 3) updateResidualTargets(outboundResult, returnResult, system);
            }
        }

        private void updateResidualTargets(LinearAttackExecutor.SegmentExecutionResult outbound,
                                           LinearAttackExecutor.SegmentExecutionResult returned,
                                           AbilitySystemServer system) {
            var current = new HashSet<UUID>();
            for (var entity : outbound.hits()) {
                if (entity instanceof LivingEntity living) {
                    current.add(living.getUUID());
                    beamTargets.put(living.getUUID(), living);
                }
            }
            for (var entity : returned.hits()) {
                if (entity instanceof LivingEntity living) {
                    current.add(living.getUUID());
                    beamTargets.put(living.getUUID(), living);
                }
            }
            var iterator = beamTargets.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (current.contains(entry.getKey())) continue;
                var target = entry.getValue();
                iterator.remove();
                if (!target.isAlive() || !RadiationIntensify.isMarked(target, initialLevel.getGameTime())) continue;
                var damage = BASE_DAMAGE * 0.2f
                        * system.getPlayerDamageMultiplier(player.getUUID());
                for (var delay = 20; delay <= 60; delay += 20) {
                    TimedSkillEffectRuntime.schedule(player, delay, () -> {
                        if (target.isAlive() && target.level() == initialLevel) {
                            target.hurtServer(initialLevel,
                                    SkillDamageSource.of(player, Skills.PARTICLE_WAVE_CANNON.get()), damage);
                        }
                    });
                }
            }
        }

        private void updateVisual(ResolvedLinearAttack attack) {
            if (attack.isReflected()) {
                var returnSegment = attack.returnSegment().orElseThrow();
                visual.setReflection(
                        (float) attack.outbound().length(),
                        (float) attack.returnVisualLength(),
                        returnSegment.direction()
                );
            } else visual.clearReflection();
        }

        private void beginBeam() {
            beaming = true;
            visual = ContinuousBeam.spawnFromMainHand(initialLevel, player, 3.0f, maximumLength);
            ContinuousBeam.followFromMainHand(player, visual, maximumLength, 0.2f);
        }

        private void end() {
            if (ended) return;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            Server.CONTEXT_MAP.remove(player, this);
            ContinuousBeam.kill(visual);
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
            return PacketTypes.PARTICLE_WAVE_CANNON_START.get();
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
            return PacketTypes.PARTICLE_WAVE_CANNON_STOP.get();
        }
    }
}
