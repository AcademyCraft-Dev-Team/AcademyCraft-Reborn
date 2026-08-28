package org.academy.internal.common.ability.aeromanip.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
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
import java.util.UUID;
import java.util.WeakHashMap;

/** Radial compressed-air rejection with escalating lift and low-drag control. */
public final class RejectingWind extends Skill {
    private static final double BASE_RADIUS = 8.0;

    public RejectingWind() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(10)
                .maxStacks(4)
                .dependsOn(Skills.TAILWIND_FIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }

    static float baseDamage(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> 3.0f;
            case HALF -> 5.0f;
            case FULL -> 7.0f;
        };
    }

    static double horizontalForce(AeromanipChargeTier tier, boolean milestoneThree) {
        var force = switch (tier) {
            case INSTANT -> 0.65;
            case HALF -> 1.15;
            case FULL -> 1.65;
        };
        return milestoneThree ? force * 1.2 : force;
    }

    static double verticalForce(AeromanipChargeTier tier) {
        return switch (tier) {
            case INSTANT -> 0.08;
            case HALF -> 0.38;
            case FULL -> 0.62;
        };
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var binding = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_B,
                        InputSystem.ANY_ACTION, 0));
        if (binding.action() != InputSystem.ANY_ACTION) {
            binding = new InputSystem.KeyCombination(
                    binding.type(), binding.keys(), InputSystem.ANY_ACTION, binding.modifiers(),
                    binding.availableWhenScreen(), binding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, binding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST, binding, _ -> Client.start(), _ -> Client.stop());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.REJECTING_WIND.get(),
                        List.of(TailwindField.Client.SKILL_INFO),
                        R.textures.atmosphere_blast_gun_icon,
                        130,
                        104));
        public static final String KEY_NAME_CAST = SkillNames.REJECTING_WIND + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.REJECTING_WIND.get())) {
                AeromanipChargeHud.begin(Skills.REJECTING_WIND.get());
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            }
        }

        private static void stop() {
            AeromanipChargeHud.end(Skills.REJECTING_WIND.get());
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

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
        private static final Map<UUID, LowDragContext> LOW_DRAG = new java.util.HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.REJECTING_WIND.get();
            if (CHARGES.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new ChargeContext(player);
            CHARGES.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CHARGES.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            if (player == null) return false;
            var skill = Skills.REJECTING_WIND.get();
            return skill.executeActiveWithResource(
                    player,
                    _ -> 25.0f * AeromanipConfig.cpMultiplier(player, SkillNames.REJECTING_WIND),
                    _ -> 24.0f,
                    (_, _) -> cast(player, skill, AeromanipChargeTier.INSTANT));
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.REJECTING_WIND.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                var skill = Skills.REJECTING_WIND.get();
                var cp = switch (tier) {
                    case INSTANT -> 25.0f;
                    case HALF -> 40.0f;
                    case FULL -> 60.0f;
                };
                var air = switch (tier) {
                    case INSTANT -> 24.0f;
                    case HALF -> 44.0f;
                    case FULL -> 68.0f;
                };
                skill.executeActiveWithResource(
                        player,
                        _ -> cp * AeromanipConfig.cpMultiplier(player, SkillNames.REJECTING_WIND),
                        _ -> air,
                        (_, _) -> cast(player, skill, tier));
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.AIRFLOW_IMPACT.get(), SoundSource.PLAYERS,
                        0.7f, tier == AeromanipChargeTier.FULL ? 0.65f : 0.85f);
                AeromanipVfx.burst(player.level(),
                        player.position().add(0.0, player.getBbHeight() * 0.45, 0.0),
                        tier == AeromanipChargeTier.FULL ? 1.35 : 0.82);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static void cast(ServerPlayer player, RejectingWind skill, AeromanipChargeTier tier) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var radius = BASE_RADIUS * (milestone >= 1 ? 1.25 : 1.0)
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.REJECTING_WIND);
            var durationScale = (milestone >= 2 ? 1.5f : 1.0f)
                    * AeromanipConfig.durationMultiplier(player, SkillNames.REJECTING_WIND);
            var source = SkillDamageSource.of(player, skill);
            var system = AbilitySystemServer.getSystem(player);
            var power = system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            var damage = baseDamage(tier)
                    * AeromanipConfig.damageMultiplier(player, SkillNames.REJECTING_WIND)
                    * power;
            var center = player.getBoundingBox().getCenter();
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    target -> target != player && target.isAlive()
                            && target.distanceToSqr(player) <= radius * radius
                            && AeromanipTargeting.canAffectNegatively(player, target));
            var cap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
            var handled = 0;
            for (var target : targets) {
                if (handled++ >= cap) break;
                if (!target.hurtServer(level, source, damage)) continue;
                skill.onHurt(player, target, damage);
                var radial = target.getBoundingBox().getCenter().subtract(center);
                if (radial.lengthSqr() <= 1.0e-8) radial = player.getLookAngle();
                radial = new Vec3(radial.x, 0.0, radial.z);
                if (radial.lengthSqr() <= 1.0e-8) radial = new Vec3(0.0, 0.0, 1.0);
                var force = AeromanipTargeting.forceMultiplier(player, target);
                if (force > 0.0) {
                    AeromanipTargeting.addClampedVelocity(target,
                            radial.normalize().scale(horizontalForce(tier, milestone >= 3) * force)
                                    .add(0.0, verticalForce(tier) * force, 0.0));
                }
                var slowDuration = Math.max(1, Math.round(
                        (tier == AeromanipChargeTier.INSTANT ? 40 : 60) * durationScale));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowDuration,
                        tier == AeromanipChargeTier.INSTANT ? 0 : 1));
                if (tier == AeromanipChargeTier.FULL) {
                    var liftDuration = Math.max(1, Math.round(50 * durationScale));
                    target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, liftDuration, 0));
                    beginLowDrag(player, target, milestone >= 3 ? liftDuration + 20 : liftDuration);
                }
            }
            level.playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_IMPACT.get(),
                    SoundSource.PLAYERS, 1.1f, tier == AeromanipChargeTier.FULL ? 0.55f : 0.8f);
            AeromanipVfx.burst(level, center, radius * 0.62);
            AeromanipVfx.ring(level, center, radius);
        }

        private static void beginLowDrag(ServerPlayer owner, LivingEntity target, int duration) {
            var previous = LOW_DRAG.remove(target.getUUID());
            if (previous != null) previous.end();
            var context = new LowDragContext(owner, target, duration);
            LOW_DRAG.put(target.getUUID(), context);
            AbilitySystemServer.registerContext(context);
        }

        private static final class LowDragContext extends ServerContext {
            private final LivingEntity target;
            private final int duration;
            private int age;
            private boolean ended;

            private LowDragContext(ServerPlayer owner, LivingEntity target, int duration) {
                super(owner);
                this.target = target;
                this.duration = Math.max(1, duration);
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                if (ended || ++age >= duration || !target.isAlive()
                        || target.level() != player.level() || player.hasDisconnected()) {
                    end();
                    return;
                }
                var speed = target.getDeltaMovement().length();
                if (speed > 0.04 && speed < 2.6) {
                    EntityMotionGuard.runWithMotionSource(
                            player, () -> AeromanipTargeting.scaleVelocity(target, 1.035));
                }
            }

            private void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @Override
            protected void onUnregistered() {
                ended = true;
                LOW_DRAG.remove(target.getUUID(), this);
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
            return PacketTypes.REJECTING_WIND_START.get();
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
            return PacketTypes.REJECTING_WIND_STOP.get();
        }
    }
}
