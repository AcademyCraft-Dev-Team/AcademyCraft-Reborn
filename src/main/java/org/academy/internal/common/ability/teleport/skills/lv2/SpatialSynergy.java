package org.academy.internal.common.ability.teleport.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.LinkedHashSet;
import java.util.Set;

public class SpatialSynergy extends Skill {
    private static final float RADIUS = 2.0f;
    private static final float EXTRA_CP_COST_PER_PLAYER = 0.5f;

    public SpatialSynergy() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .passive()
                .maintenanceCost(20)
                .maxSkillLevel(0)
                .dependsOn(Skills.SELF_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.SPATIAL_SYNERGY + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SpatialSynergy.Client.Config getDefault() {
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
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.SPATIAL_SYNERGY.get().toggle(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onEntityTeleport(EntityTeleportEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.SPATIAL_SYNERGY.get();
            if (!skill.isEnabled(player)) return;

            var targetPos = event.getTarget();
            var level = player.level();

            var box = player.getBoundingBox().inflate(RADIUS);
            var nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, box,
                    p -> p != player && p.isAlive());

            if (nearbyPlayers.isEmpty()) return;

            var system = AbilitySystemServer.getSystem(player);
            var maxCP = system.getPlayerMaxCP(player.getUUID());

            for (var nearby : nearbyPlayers) {
                var extraCost = EXTRA_CP_COST_PER_PLAYER * maxCP;
                var availableCP = system.getPlayerAvailableCP(player.getUUID());
                if (availableCP >= extraCost) {
                    system.setPlayerAvailableCP(player.getUUID(), availableCP - extraCost);
                    var dimensions = nearby.getDimensions(Pose.STANDING);
                    var teleportY = targetPos.y() - (dimensions.height() / 2.0);
                    nearby.teleportTo(targetPos.x(), teleportY, targetPos.z());
                    nearby.resetFallDistance();
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.SPATIAL_SYNERGY_TOGGLE.get();
        }
    }
}
