package org.academy.internal.common.ability.teleport.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
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

import java.util.*;

public class SpacialReplace extends Skill {
    public SpacialReplace() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .cpCost(300)
                .iterationTicks(60)
                .maxStacks(1)
                .dependsOn(Skills.COORDINATE_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_CORNER1, Client.CONFIG.getKeyBinding(Client.KEY_CORNER1,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_KP_1)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onSelectCorner1);

        InputSystem.addKeyBinding(Client.KEY_CORNER2, Client.CONFIG.getKeyBinding(Client.KEY_CORNER2,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_KP_2)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onSelectCorner2);

        InputSystem.addKeyBinding(Client.KEY_PASTE, Client.CONFIG.getKeyBinding(Client.KEY_PASTE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_P)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL)))))
        , Client::onPaste);
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_CORNER1 = SkillNames.SPACIAL_REPLACE + "_corner1";
        public static final String KEY_CORNER2 = SkillNames.SPACIAL_REPLACE + "_corner2";
        public static final String KEY_PASTE = SkillNames.SPACIAL_REPLACE + "_paste";
        public static Config CONFIG = new Config();

        private static BlockPos getLookBlock() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.hitResult == null) return null;
            if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
                return ((BlockHitResult) mc.hitResult).getBlockPos();
            }
            return null;
        }

        public static void onSelectCorner1() {
            var pos = getLookBlock();
            if (pos != null) MisakaNetworkClient.sendPacket(new SetCornerPacket(1, pos));
        }

        public static void onSelectCorner2() {
            var pos = getLookBlock();
            if (pos != null) MisakaNetworkClient.sendPacket(new SetCornerPacket(2, pos));
        }

        public static void onPaste() {
            var pos = getLookBlock();
            if (pos != null) MisakaNetworkClient.sendPacket(new PastePacket(pos));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SpacialReplace.Client.Config getDefault() {
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
        private static final Map<Player, BlockPos[]> SELECTIONS = new WeakHashMap<>();

        @SubscribePacket
        public static void handleSetCorner(SetCornerPacket p) {
            var player = p.getPacketListener().getPlayer();
            var corners = SELECTIONS.computeIfAbsent(player, k -> new BlockPos[2]);
            corners[p.getCorner() - 1] = p.getPosition();
        }

        @SubscribePacket
        public static void handlePaste(PastePacket p) {
            var player = p.getPacketListener().getPlayer();
            var corners = SELECTIONS.get(player);
            if (corners == null || corners[0] == null || corners[1] == null) return;

            Skills.SPACIAL_REPLACE.get().executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                var minX = Math.min(corners[0].getX(), corners[1].getX());
                var minY = Math.min(corners[0].getY(), corners[1].getY());
                var minZ = Math.min(corners[0].getZ(), corners[1].getZ());
                var maxX = Math.max(corners[0].getX(), corners[1].getX());
                var maxY = Math.max(corners[0].getY(), corners[1].getY());
                var maxZ = Math.max(corners[0].getZ(), corners[1].getZ());

                var targetOrigin = p.getTarget();
                var dx = targetOrigin.getX() - minX;
                var dy = targetOrigin.getY() - minY;
                var dz = targetOrigin.getZ() - minZ;

                var blocksToCopy = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();

                for (var x = minX; x <= maxX; x++) {
                    for (var y = minY; y <= maxY; y++) {
                        for (var z = minZ; z <= maxZ; z++) {
                            var from = new BlockPos(x, y, z);
                            var state = level.getBlockState(from);
                            if (state.isAir()) continue;
                            blocksToCopy.put(from, state);
                        }
                    }
                }

                for (var entry : blocksToCopy.entrySet()) {
                    var from = entry.getKey();
                    var to = from.offset(dx, dy, dz);
                    level.setBlock(to, entry.getValue(), 3);
                    level.removeBlock(from, false);
                }

                SELECTIONS.remove(player);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetCornerPacket extends Packet<ServerGamePacketListenerImpl, SetCornerPacket> {
        public static final StreamCodec<ByteBuf, SetCornerPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, SetCornerPacket::getCorner,
                BlockPos.STREAM_CODEC, SetCornerPacket::getPosition,
                SetCornerPacket::new
        );

        private final int corner;
        private final BlockPos position;

        public SetCornerPacket(int corner, BlockPos position) {
            this.corner = corner;
            this.position = position;
        }

        public int getCorner() { return corner; }
        public BlockPos getPosition() { return position; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetCornerPacket> getPacketType() {
            return PacketTypes.SPACIAL_REPLACE_SET_CORNER.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PastePacket extends Packet<ServerGamePacketListenerImpl, PastePacket> {
        public static final StreamCodec<ByteBuf, PastePacket> CODEC =
                BlockPos.STREAM_CODEC.map(PastePacket::new, PastePacket::getTarget);

        private final BlockPos target;

        public PastePacket(BlockPos target) { this.target = target; }
        public BlockPos getTarget() { return target; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PastePacket> getPacketType() {
            return PacketTypes.SPACIAL_REPLACE_PASTE.get();
        }
    }
}
