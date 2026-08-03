package org.academy.internal.common.ability.teleport.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.DistortionEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class VisualTeleport extends Skill {
    public static final double MAX_DISTANCE = 16.0;

    public VisualTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(0)
                .iterationTicks(6)
                .maxStacks(1)
        );
    }

    @Override
    public float getCpCost(int skillLevel) {
        return 0;
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_SHIFT)),
                _ -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.VISUAL_TELEPORT + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.VISUAL_TELEPORT.get())) return;
            MisakaNetworkClient.send(TeleportPacket.INSTANCE);
            var player = mc.player;
            DistortionEffectWrapper.INSTANCE.trigger(
                    (float) player.getX(), (float) player.getY() + 1.0f, (float) player.getZ(),
                    0.9f, 0.8f,
                    0.45f, 0.2f, 0.8f, 0.7f,
                    0.1f, 0.0f, 0.3f, 0.0f
            );
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
        @SubscribePacket
        public static void handle(TeleportPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var target = resolveTarget(player);
            if (target == null) return;

            Skills.VISUAL_TELEPORT.get().executeActive(player, (_, _) -> {
                player.teleportTo(target.x, target.y, target.z);
                player.resetFallDistance();
                player.setDeltaMovement(0, 0.25, 0);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.level().playSound(null, player.blockPosition(), SoundEvents.SELF_TELEPORT.get(),
                        SoundSource.PLAYERS, 0.8f, 1.2f);
            });
        }

        private static net.minecraft.world.phys.Vec3 resolveTarget(
                net.minecraft.server.level.ServerPlayer player
        ) {
            var direction = player.getLookAngle().normalize();
            if (direction.lengthSqr() < 1.0e-6) return null;
            var eye = player.getEyePosition();
            var rayEnd = eye.add(direction.scale(MAX_DISTANCE));
            var hit = player.level().clip(new ClipContext(
                    eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));
            var distance = Math.min(MAX_DISTANCE, eye.distanceTo(hit.getLocation()));
            for (var d = Math.max(0.0, distance - 0.5); d >= 0.0; d -= 0.5) {
                var desired = player.position().add(direction.scale(d));
                var safe = TeleportSafety.findSafe(player, desired);
                if (safe != null) return safe;
            }
            return null;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TeleportPacket extends Packet<ServerGamePacketListenerImpl, TeleportPacket> {
        public static final TeleportPacket INSTANCE = new TeleportPacket();
        public static final StreamCodec<ByteBuf, TeleportPacket> CODEC = StreamCodec.unit(INSTANCE);

        private TeleportPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TeleportPacket> getPacketType() {
            return PacketTypes.VISUAL_TELEPORT.get();
        }
    }
}
