package org.academy.internal.common.ability.accelerator.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.network.annotation.SubscribePacket;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public class BloodflowReverse extends Skill {
    public BloodflowReverse() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get()).level(AbilityLevel.LEVEL2));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME, Client.CONFIG.getKeyBinding(Client.KEY_NAME,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_RELEASE,
                                        new LinkedHashSet<>(
                                                Set.of(GLFW.GLFW_MOD_ALT)
                                        )
                                )
                        )
                ), Client::reverseBloodflow
        );
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = SkillNames.BLOODFLOW_REVERSE + "_use";
        public static Config CONFIG = new Config();

        public static void reverseBloodflow() {
            AcademyCraftClient.sendPacket(ReverseBloodflowPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull BloodflowReverse.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @SuppressWarnings("resource")
        @SubscribePacket
        public static void onAction(ReverseBloodflowPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var hitResult = player.pick(1, 1, false);
            var entityList = player.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(new BlockPos((int) hitResult.getLocation().x, (int) hitResult.getLocation().y, (int) hitResult.getLocation().z))
            );
            if (!entityList.isEmpty()) {
                var livingEntity = entityList.getFirst();
                if (livingEntity != player) {
                    livingEntity.hurt(new DamageSource(player.damageSources().damageTypes.getOrThrow(DamageTypes.MAGIC)), livingEntity.getHealth());
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReverseBloodflowPacket extends Packet<ServerGamePacketListenerImpl, ReverseBloodflowPacket> {
        public static final ReverseBloodflowPacket INSTANCE = new ReverseBloodflowPacket();
        public static final StreamCodec<ByteBuf, ReverseBloodflowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReverseBloodflowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReverseBloodflowPacket> getPacketType() {
            return PacketTypes.REVERSE_BLOODFLOW.get();
        }
    }
}