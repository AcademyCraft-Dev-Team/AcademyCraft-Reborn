package org.academy.internal.common.ability.accelerator.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.TrailEffectWrapper;
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

import java.util.Map;
import java.util.WeakHashMap;

public class HyperAccelerate extends Skill {
    public static final long MAX_CHARGE_TICKS = 40;

    public HyperAccelerate() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(50)
                .iterationTicks(8)
                .maxStacks(1)
                .dependsOn(Skills.VECTOR_ACCEL)
        );
    }

    @Override
    public int getMaxStacks(int skillLevel) {
        if (skillLevel >= 2) return 2;
        return super.getMaxStacks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);

        InputSystem.addKeyBinding(Client.KEY_NAME_PRESS, Client.CONFIG.getKeyBinding(Client.KEY_NAME_PRESS,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_C, InputConstants.PRESS, InputConstants.MOD_SHIFT)
        ), ctx -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_C, InputConstants.RELEASE, InputConstants.MOD_SHIFT)
        ), ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_PRESS = SkillNames.HYPER_ACCELERATE + "_press";
        public static final String KEY_NAME_USE = SkillNames.HYPER_ACCELERATE + "_use";
        public static Config CONFIG = new Config();

        public static void onChargeStart() {
            if (!org.academy.api.client.ability.AbilitySystemClient.canUseSkill(
                    Skills.HYPER_ACCELERATE.get())) return;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            MisakaNetworkClient.send(LaunchPacket.INSTANCE);
            var p = mc.player;
            var trail = TrailEffectWrapper.INSTANCE.createTrail(0.8f, 0.15f, 0.3f, 0.7f, 1.0f);
            trail.addPoint((float) p.getX(), (float) p.getY(), (float) p.getZ());
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public HyperAccelerate.Client.Config getDefault() {
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
        public static final double MAX_LAUNCH_SPEED = 3.0;
        private static final Map<ServerPlayer, Long> CHARGE_START_TICKS = new WeakHashMap<>();

        public static float getChargeRatio(long startTick, long releaseTick) {
            return Math.clamp((float) Math.max(0, releaseTick - startTick) / MAX_CHARGE_TICKS, 0.1f, 1.0f);
        }

        public static double getLaunchSpeed(float chargeRatio) {
            return MAX_LAUNCH_SPEED * (0.5 + 0.5 * Math.clamp(chargeRatio, 0.1f, 1.0f));
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.HYPER_ACCELERATE.get().isEnabled(player)) return;
            CHARGE_START_TICKS.put(player, player.level().getGameTime());
        }

        @SubscribePacket
        public static void handle(LaunchPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var startTick = CHARGE_START_TICKS.remove(player);
            if (startTick == null) return;
            var chargeRatio = getChargeRatio(startTick, player.level().getGameTime());
            Skills.HYPER_ACCELERATE.get().executeActive(player, (ctx, actualCost) -> {
                var speed = getLaunchSpeed(chargeRatio);
                var velocity = player.getLookAngle().normalize().scale(speed);
                player.setDeltaMovement(velocity);
                player.resetFallDistance();
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                var nearby = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(3.0), e -> e != player && e.isAlive());
                for (var target : nearby) {
                    target.setDeltaMovement(target.position().subtract(player.position()).normalize().scale(0.5));
                    target.hurtMarked = true;
                    var system = AbilitySystemServer.getSystem(player);
                    var damage = 4.0f
                            * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                            * system.getPlayerDamageMultiplier(player.getUUID());
                    target.hurtServer(player.level(),
                            SkillDamageSource.of(player, Skills.HYPER_ACCELERATE.get()), damage);
                }
            });
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
            return PacketTypes.HYPER_ACCELERATE_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class LaunchPacket extends Packet<ServerGamePacketListenerImpl, LaunchPacket> {
        public static final LaunchPacket INSTANCE = new LaunchPacket();
        public static final StreamCodec<ByteBuf, LaunchPacket> CODEC = StreamCodec.unit(INSTANCE);

        private LaunchPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, LaunchPacket> getPacketType() {
            return PacketTypes.HYPER_ACCELERATE_LAUNCH.get();
        }
    }
}
