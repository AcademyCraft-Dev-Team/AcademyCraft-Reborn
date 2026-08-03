package org.academy.internal.common.ability.teleport.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
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

public class ClipThrough extends Skill {
    public ClipThrough() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(150)
                .iterationTicks(5)
                .maxStacks(1)
        );
    }

    public static float getMaxDistance(int level) {
        return 2.0f + level;
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_F, InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.CLIP_THROUGH + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.CLIP_THROUGH.get())) return;
            MisakaNetworkClient.send(TeleportPacket.INSTANCE);
            var player = mc.player;
            DistortionEffectWrapper.INSTANCE.trigger(
                    (float) player.getX(), (float) player.getY() + 1.0f, (float) player.getZ(),
                    0.75f, 0.7f,
                    0.35f, 0.25f, 0.75f, 0.65f,
                    0.08f, 0.0f, 0.25f, 0.0f
            );
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ClipThrough.Client.Config getDefault() {
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
            var skill = Skills.CLIP_THROUGH.get();
            if (!skill.isEnabled(player)) return;
            var dir = player.getLookAngle().normalize();
            if (dir.lengthSqr() < 1.0e-6) return;
            var safe = TeleportSafety.findSafe(
                    player,
                    player.position().add(dir.scale(getMaxDistance(skill.getLevel(player))))
            );
            if (safe == null) return;
            skill.executeActive(player, (_, _) -> {
                player.teleportTo(safe.x, safe.y, safe.z);
                player.resetFallDistance();
                player.setDeltaMovement(dir.scale(0.1));
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.level().playSound(null, player.blockPosition(), SoundEvents.PENETRATE_TELEPORT.get(),
                        SoundSource.PLAYERS, 0.75f, 1.25f);
            });
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
            return PacketTypes.CLIP_THROUGH_TELEPORT.get();
        }
    }
}
