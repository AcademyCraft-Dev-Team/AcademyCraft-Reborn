package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BloodflowReverse extends Skill {
    public BloodflowReverse() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get()).level(AbilityLevel.LEVEL2));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(key);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.Config();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(key, Client.CONFIG);
        }

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
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = SkillNames.BLOODFLOW_REVERSE + "_use";
        public static Config CONFIG = new Config();

        public static void reverseBloodflow() {
            AcademyCraftClient.sendPacket(new C2SPacket(new ReverseBloodflowPacket()));
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
            ServerPlayer player = packet.getPacketListener().getPlayer();
            HitResult hitResult = player.pick(1, 1, false);
            List<LivingEntity> entityList = player.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(new BlockPos((int) hitResult.getLocation().x, (int) hitResult.getLocation().y, (int) hitResult.getLocation().z))
            );
            if (!entityList.isEmpty()) {
                LivingEntity livingEntity = entityList.getFirst();
                if (livingEntity != player) {
                    livingEntity.hurt(new DamageSource(player.damageSources().damageTypes.getHolderOrThrow(DamageTypes.MAGIC)), livingEntity.getHealth());
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReverseBloodflowPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public ReverseBloodflowPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public ReverseBloodflowPacket() {
            super(null);
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends IPacket<ServerGamePacketListenerImpl>> getPacketType() {
            return PacketTypes.REVERSE_BLOODFLOW.get();
        }
    }
}