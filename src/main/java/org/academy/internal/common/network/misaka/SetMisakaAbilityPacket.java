package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.server.ability.EntityAbilityService;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class SetMisakaAbilityPacket extends Packet<ServerGamePacketListenerImpl, SetMisakaAbilityPacket> {
    public static final StreamCodec<ByteBuf, SetMisakaAbilityPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC, p -> p.entityUuid,
            ByteBufCodecs.STRING_UTF8, p -> p.skill, ByteBufCodecs.BOOL, p -> p.enabled, SetMisakaAbilityPacket::new);
    private static boolean initialized;
    private final UUID entityUuid;
    private final String skill;
    private final boolean enabled;
    public SetMisakaAbilityPacket(UUID entityUuid, String skill, boolean enabled) {
        this.entityUuid = entityUuid; this.skill = skill; this.enabled = enabled;
    }
    public static synchronized void initServer() {
        if (!initialized) { initialized = true; MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    }
    @Override
    public PacketType<ServerGamePacketListenerImpl, SetMisakaAbilityPacket> getPacketType() { return PacketTypes.SET_MISAKA_ABILITY.get(); }
    public static final class Server {
        @SubscribePacket
        public static void handle(SetMisakaAbilityPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var session = MisakaPanelSupport.load(player, packet.entityUuid);
            var skill = Identifier.tryParse(packet.skill);
            if (session == null || skill == null || !session.inRange(MisakaSisterEntity.PANEL_RANGE_SQR)
                    || !session.allow(InteractionGate.Intent.STATE)) return;
            var state = EntityAbilityService.inspect(session.sister()).orElse(null);
            if (state == null || !state.knows(skill)) return;
            EntityAbilityService.setEnabled(session.sister(), skill, packet.enabled);
            MisakaPanelSupport.sendPanel(player, session.sister());
        }
    }
}
