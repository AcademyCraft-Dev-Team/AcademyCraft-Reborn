package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.time.TemporalClientRuntime;
import org.academy.internal.server.time.TemporalRuntime;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

/** Full authoritative immunity snapshot; revisions discard stale delivery. */
@PacketTarget(ThreadType.CLIENT)
public final class TemporalImmunitySyncPacket
        extends Packet<ClientPacketListener, TemporalImmunitySyncPacket> {
    private static final StreamCodec<ByteBuf, Map<UUID, Integer>> MASKS_CODEC =
            ByteBufCodecs.map(
                    (IntFunction<Map<UUID, Integer>>) HashMap::new,
                    UUIDUtil.STREAM_CODEC,
                    ByteBufCodecs.VAR_INT,
                    4096
            );
    public static final StreamCodec<ByteBuf, TemporalImmunitySyncPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    TemporalImmunitySyncPacket::revision,
                    MASKS_CODEC,
                    TemporalImmunitySyncPacket::masks,
                    TemporalImmunitySyncPacket::new
            );
    private static boolean clientInitialized;

    private final long revision;
    private final Map<UUID, Integer> masks;

    public TemporalImmunitySyncPacket(long revision, Map<UUID, Integer> masks) {
        this.revision = revision;
        this.masks = Map.copyOf(masks);
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static void broadcast(
            MinecraftServer server,
            TemporalRuntime.ClientStateSnapshot snapshot
    ) {
        var packet = new TemporalImmunitySyncPacket(
                snapshot.revision(),
                snapshot.masks()
        );
        for (var player : server.getPlayerList().getPlayers()) {
            MisakaNetworkServer.send(player, packet);
        }
    }

    public long revision() {
        return revision;
    }

    public Map<UUID, Integer> masks() {
        return masks;
    }

    @Override
    public PacketType<ClientPacketListener, TemporalImmunitySyncPacket> getPacketType() {
        return PacketTypes.TEMPORAL_IMMUNITY_SYNC.get();
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var server = player.level().getServer();
            var context = (MinecraftServerContext) server;
            if (!context.hasAcademyCraftServer()) return;
            var runtime = (TemporalRuntime) context.getAcademyCraftServer()
                    .getTemporalService();
            var snapshot = runtime.clientStateSnapshot();
            MisakaNetworkServer.send(player, new TemporalImmunitySyncPacket(
                    snapshot.revision(),
                    snapshot.masks()
            ));
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void receive(TemporalImmunitySyncPacket packet) {
            TemporalClientRuntime.applyState(packet.revision, packet.masks);
        }
    }
}
