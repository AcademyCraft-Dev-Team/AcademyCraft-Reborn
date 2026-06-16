package org.academy.internal.common.ability.electromaster.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public final class PulseCharge extends Skill {
    public static final String KEY_NAME_USE = SkillNames.PULSE_CHARGE + ".use";

    public PulseCharge() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(15)
                .iterationTicks(10)
                .maxStacks(3)
        );
    }

    public int getMaxDistance(int level) {
        return 6 + level * 2;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(KEY_NAME_USE, Client.CONFIG.getKeyBinding(KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_CONTROL)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.send(UsePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public PulseCharge.Client.Config getDefault() {
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
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = player.level();
            Skills.PULSE_CHARGE.get().executeActive(player, (ctx, actualCost) -> {
                var eyePos = player.getEyePosition();
                var maxDist = Skills.PULSE_CHARGE.get().getMaxDistance(ctx.level());
                var distance = LevelUtil.getValidViewDistance(player, maxDist);
                var targetPos = eyePos.add(player.getLookAngle().scale(distance));

                var arc = new ArcEffect(level, 10);
                arc.setPos(eyePos);
                var rootPath = new ArcPath(
                        new LinePath(eyePos.toVector3f(), targetPos.toVector3f()),
                        List.of(new JaggedModifier(1, 2, MathUtil.RANDOM.nextLong())),
                        1.5f,
                        List.of()
                );
                arc.setArcPath(rootPath);
                level.addFreshEntity(arc);

                var blockPos = BlockPos.containing(targetPos);
                level.updateNeighborsAt(blockPos, level.getBlockState(blockPos).getBlock());
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.PULSE_CHARGE_USE.get();
        }
    }
}
