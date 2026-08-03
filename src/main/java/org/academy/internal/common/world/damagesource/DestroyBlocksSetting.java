package org.academy.internal.common.world.damagesource;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class DestroyBlocksSetting {
    public static final String TAG_KEY_DESTROY_BLOCKS = "academy_destroy_blocks_enabled";
    private static boolean serverInitialized;

    private DestroyBlocksSetting() {
    }

    public static boolean isDestroyBlocksEnabled(Player player) {
        return player == null || player.getData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get());
    }

    public static boolean canDestroyBlocks(ServerPlayer player) {
        if (!isDestroyBlocksEnabled(player)) return false;
        try {
            var server = player.level().getServer();
            if (server == null || server.getAcademyCraftServer() == null) return true;
            return server.getAcademyCraftServer().getGenericConfig().booleanMap
                    .getOrDefault("destroyBlocks", true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setDestroyBlocksEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), enabled);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get());
        }
    }

    public static void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void setDestroyBlocks(SetPacket packet) {
            setDestroyBlocksEnabled(packet.getPacketListener().getPlayer(), packet.enabled);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var legacyData = player.getPersistentData();
            if (legacyData.contains(TAG_KEY_DESTROY_BLOCKS)) {
                setDestroyBlocksEnabled(
                        player,
                        legacyData.getBoolean(TAG_KEY_DESTROY_BLOCKS).orElse(true)
                );
                legacyData.remove(TAG_KEY_DESTROY_BLOCKS);
            } else {
                player.syncData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get());
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetPacket extends Packet<ServerGamePacketListenerImpl, SetPacket> {
        public static final StreamCodec<ByteBuf, SetPacket> CODEC =
                ByteBufCodecs.BOOL.map(SetPacket::new, packet -> packet.enabled);
        private final boolean enabled;

        public SetPacket(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetPacket> getPacketType() {
            return PacketTypes.DESTROY_BLOCKS_SET.get();
        }
    }
}
