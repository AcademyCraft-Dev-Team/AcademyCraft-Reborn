package org.academy.internal.common.ability.teleport.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.CoordinateTeleportData;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class CoordinateTeleport extends Skill {
    private static final int COMPUTE_TICKS = 200;

    public CoordinateTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(0)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.SPATIAL_SYNERGY)
                .dependsOn(Skills.CUT_THROUGH)
                .withCustomData(CoordinateTeleportData.ID, CoordinateTeleportData.class,
                        player -> new CoordinateTeleportData())
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_SAVE, Client.CONFIG.getKeyBinding(Client.KEY_SAVE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_T)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onSave);

        InputSystem.addKeyBinding(Client.KEY_TP, Client.CONFIG.getKeyBinding(Client.KEY_TP,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Y)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onTeleport);
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_SAVE = SkillNames.COORDINATE_TELEPORT + "_save";
        public static final String KEY_TP = SkillNames.COORDINATE_TELEPORT + "_tp";
        public static Config CONFIG = new Config();

        public static void onSave() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var pos = mc.player.position();
            var dim = mc.player.level().dimension().toString();
            var name = String.format("(%.0f, %.0f, %.0f)", pos.x, pos.y, pos.z);
            MisakaNetworkClient.send(new SavePositionPacket(pos, dim, name));
        }

        public static void onTeleport() {
            MisakaNetworkClient.send(new RequestTeleportPacket());
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public CoordinateTeleport.Client.Config getDefault() {
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
        private static final Map<ServerPlayer, ComputeContext> COMPUTE_MAP = new WeakHashMap<>();

        @SubscribePacket
        public static void handleSave(SavePositionPacket p) {
            var player = p.getPacketListener().getPlayer();
            var data = Skills.COORDINATE_TELEPORT.get()
                    .<CoordinateTeleportData>getRuntimeData(player).orElse(null);
            if (data == null) return;

            var dim = player.level().dimension().toString();
            data.addPosition(new CoordinateTeleportData.SavedPosition(
                    p.getName(), p.getPosition().x, p.getPosition().y, p.getPosition().z, dim));
            player.sendSystemMessage(Component.translatable("academy.coordinate.saved", p.getName()));
        }

        @SubscribePacket
        public static void handleRequestTeleport(RequestTeleportPacket p) {
            var player = p.getPacketListener().getPlayer();
            var data = Skills.COORDINATE_TELEPORT.get()
                    .<CoordinateTeleportData>getRuntimeData(player).orElse(null);
            if (data == null || data.getSavedPositions().isEmpty()) {
                player.sendSystemMessage(Component.translatable("academy.coordinate.no_saved"));
                return;
            }

            var existing = COMPUTE_MAP.get(player);
            if (existing != null) {
                existing.end();
            }

            var lastPos = data.getSavedPositions().getLast();
            Skills.COORDINATE_TELEPORT.get().executeActive(player, (ctx, c) -> {
                var computeCtx = new ComputeContext(player, lastPos);
                COMPUTE_MAP.put(player, computeCtx);
                AbilitySystemServer.registerContext(computeCtx);
            });
        }
    }

    public static final class ComputeContext extends ServerContext {
        private final CoordinateTeleportData.SavedPosition target;
        private final Vec3 startPos;
        private int ticks;
        private boolean ended;

        private ComputeContext(ServerPlayer player, CoordinateTeleportData.SavedPosition target) {
            super(player);
            this.target = target;
            startPos = player.position();
            player.sendSystemMessage(Component.translatable("academy.coordinate.computing"));
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre ev) {
            ticks++;
            if (ended || player.hasDisconnected() || !player.isAlive()) {
                end();
                return;
            }

            if (player.position().distanceToSqr(startPos) > 0.25) {
                player.sendSystemMessage(Component.translatable("academy.coordinate.cancelled"));
                end();
                return;
            }

            if (ticks >= COMPUTE_TICKS) {
                player.teleportTo(target.x(), target.y(), target.z());
                player.resetFallDistance();
                player.sendSystemMessage(Component.translatable("academy.coordinate.teleported"));
                end();
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.COMPUTE_MAP.remove(player);
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SavePositionPacket extends Packet<ServerGamePacketListenerImpl, SavePositionPacket> {
        public static final StreamCodec<ByteBuf, SavePositionPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, p -> p.pos.x,
                ByteBufCodecs.DOUBLE, p -> p.pos.y,
                ByteBufCodecs.DOUBLE, p -> p.pos.z,
                ByteBufCodecs.STRING_UTF8, p -> p.dimension,
                ByteBufCodecs.STRING_UTF8, p -> p.name,
                (x, y, z, dim, name) -> new SavePositionPacket(new Vec3(x, y, z), dim, name)
        );

        private final Vec3 pos;
        private final String dimension;
        private final String name;

        public SavePositionPacket(Vec3 pos, String dimension, String name) {
            this.pos = pos;
            this.dimension = dimension;
            this.name = name;
        }

        public Vec3 getPosition() { return pos; }
        public String getDimension() { return dimension; }
        public String getName() { return name; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SavePositionPacket> getPacketType() {
            return PacketTypes.COORDINATE_TELEPORT_SAVE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestTeleportPacket extends Packet<ServerGamePacketListenerImpl, RequestTeleportPacket> {
        public static final RequestTeleportPacket INSTANCE = new RequestTeleportPacket();
        public static final StreamCodec<ByteBuf, RequestTeleportPacket> CODEC = StreamCodec.unit(INSTANCE);

        private RequestTeleportPacket() {}

        @Override
        public PacketType<ServerGamePacketListenerImpl, RequestTeleportPacket> getPacketType() {
            return PacketTypes.COORDINATE_TELEPORT_REQUEST.get();
        }
    }
}
