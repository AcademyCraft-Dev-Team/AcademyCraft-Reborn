package org.academy.internal.common.ability.program;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.ConfigSync;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.S2CPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/** Registered server player with a real mock Connection; this is not a logged-in game client or TCP peer. */
public final class CapturedProgramPlayer implements AutoCloseable {
    private static final Set<Class<?>> PROGRAM_PACKETS = Set.of(
            AbilityProgramManager.SyncPacket.class, AbilityProgramManager.ResultPacket.class);
    private static final Field WRAPPER_ID = wrapperField("id");
    private static final Field WRAPPER_BODY = wrapperField("byteBuf");
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private boolean closed;

    private CapturedProgramPlayer(ServerPlayer player, EmbeddedChannel channel) {
        this.player = player; this.channel = channel;
    }

    public static CapturedProgramPlayer create(ServerLevel level, UUID id, String name, Vec3 position) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Capture player requires server thread");
        var profile = new GameProfile(id, name);
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
        NetworkRegistry.configureMockConnection(connection);
        // A persistent mock player participates in normal server ticks. NeoForge's config
        // tracking must therefore be prepared as it is in the CONFIGURATION phase.
        var configuration = (ServerConfigurationPacketListener) Proxy.newProxyInstance(
                ServerConfigurationPacketListener.class.getClassLoader(),
                new Class<?>[]{ServerConfigurationPacketListener.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getConnection")) return connection;
                    if (method.getName().equals("send") && arguments != null && arguments.length > 0
                            && arguments[0] instanceof CustomPacketPayload payload) {
                        connection.send(new ClientboundCustomPayloadPacket(payload));
                        return null;
                    }
                    throw new UnsupportedOperationException("Unexpected mock configuration call: " + method);
                });
        ConfigSync.syncAllConfigs(configuration);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // PlayerList membership alone does not tick the play listener. The server's normal
        // connection loop owns Connection.tick -> listener.tick -> ServerPlayer.doTick.
        level.getServer().getConnection().getConnections().add(connection);
        player.connection.markClientLoaded();
        player.snapTo(position.x, position.y, position.z, 0, 0);
        player.setNoGravity(true);
        return new CapturedProgramPlayer(player, channel);
        } catch (RuntimeException | Error failure) {
            level.getServer().getConnection().getConnections().remove(connection);
            try {
                if (level.getServer().getPlayerList().getPlayer(id) == player) {
                    level.getServer().getPlayerList().remove(player);
                }
            } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    public ServerPlayer player() { return player; }
    public EmbeddedChannel channel() { return channel; }

    public record Captured(ProgramNetworkMetrics.Kind kind, int misakaId, int payloadBytes,
                           int envelopeBytes, Packet<?, ?> packet) {}

    /**
     * Drains actual outbound S2CPacket objects. The sender already executed its real CODEC.
     * Observers decode a read-only duplicate of that body, without the wrapper's extra copy.
     * Envelope size adds the actual registry VarInt id; it excludes Minecraft/TCP framing.
     * This helper owns drained wrappers and releases their private bodies after observation.
     */
    public List<Captured> drain() {
        if (closed) return List.of();
        channel.runPendingTasks();
        return ProgramNetworkMetrics.observeUnprofiled(() -> {
            var result = new ArrayList<Captured>();
            long bytes = 0;
            int messages = 0;
            for (Object value; (value = channel.readOutbound()) != null;) {
                try {
                    if (++messages > 32768) throw new IllegalStateException("Capture outbound queue exceeded its observation budget");
                    if (!(value instanceof S2CPacket wrapper)) continue;
                    var body = wrapperBody(wrapper);
                    try {
                        var buffer = body.asReadOnly();
                        int id = wrapperId(wrapper);
                        int payloadBytes = buffer.readableBytes();
                        int envelopeBytes = payloadBytes + VarInt.getByteSize(id);
                        var type = NetworkSystem.getPacketTypeById(id);
                        if (!PROGRAM_PACKETS.contains(type.packetClass())) continue;
                        var packet = (Packet<?, ?>) type.codec().decode(buffer);
                        if (buffer.isReadable()) throw new IllegalStateException("Program capture codec left trailing bytes");
                        bytes += payloadBytes;
                        if (result.size() >= 4096 || bytes > 16 * 1024 * 1024) {
                            throw new IllegalStateException("Program capture payload exceeded its observation budget");
                        }
                        result.add(new Captured(ProgramNetworkMetrics.kind(packet), id, payloadBytes, envelopeBytes, packet));
                    } finally { body.release(); }
                } finally { ReferenceCountUtil.release(value); }
            }
            return List.copyOf(result);
        });
    }

    @Override public void close() {
        if (closed) return;
        // Cleanup only: GameTest callbacks may run between vanilla suspend/resumeFlushing.
        // Flush our removed peer before taking final ownership of its queued wrapper bodies.
        try { player.connection.getConnection().flushChannel(); drain(); }
        finally {
            try {
                player.level().getServer().getConnection().getConnections().remove(player.connection.getConnection());
                player.level().getServer().getPlayerList().remove(player);
                player.connection.getConnection().flushChannel();
                drain();
            } finally { closed = true; channel.finishAndReleaseAll(); }
        }
    }

    private static Field wrapperField(String name) {
        try {
            var field = S2CPacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static ByteBuf wrapperBody(S2CPacket packet) {
        try { return (ByteBuf) WRAPPER_BODY.get(packet); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }

    private static int wrapperId(S2CPacket packet) {
        try { return WRAPPER_ID.getInt(packet); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
}
