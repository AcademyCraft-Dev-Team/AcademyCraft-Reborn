package org.academy.internal.common.ability.teleport.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.teleport.AreaTeleportState;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class AreaTeleportSetup {
    private AreaTeleportSetup() {
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(MarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.AREA_TELEPORT_SELECT.get().isEnabled(player)
                    || AreaTeleportState.selected(player.getUUID()) == null) return;
            var skill = Skills.AREA_TELEPORT_SELECT.get();
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var policy = ProficiencyPolicy.server(player);
            if (packet.action == MarkPacket.ACTION_SWAP) {
                if (milestone >= 3 && policy.allowAreaTeleportSwap()) {
                    var enabled = AreaTeleportState.toggleSwap(player.getUUID());
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            enabled
                                    ? "message.academy.area_teleport.swap_enabled"
                                    : "message.academy.area_teleport.swap_disabled"));
                    AreaTeleportSelect.Server.sync(player);
                }
                return;
            }
            if (packet.action == MarkPacket.ACTION_TRANSFORM) {
                if (milestone < 2 || !policy.allowAreaTeleportTransforms()) return;
                AreaTeleportState.cycleTransform(player.getUUID(), milestone >= 3);
                AreaTeleportSelect.Server.sync(player);
                return;
            }
            var pos = AreaTeleportSelect.Server.pickBlock(player);
            if (pos == null) return;
            AreaTeleportState.setDestination(player.getUUID(), player.level().dimension(), pos);
            AreaTeleportSelect.Server.sync(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MarkPacket extends Packet<ServerGamePacketListenerImpl, MarkPacket> {
        private static final int ACTION_MARK = 0;
        private static final int ACTION_TRANSFORM = 1;
        private static final int ACTION_SWAP = 2;
        public static final MarkPacket MARK = new MarkPacket(ACTION_MARK);
        public static final MarkPacket TRANSFORM = new MarkPacket(ACTION_TRANSFORM);
        public static final MarkPacket TOGGLE_SWAP = new MarkPacket(ACTION_SWAP);
        public static final StreamCodec<ByteBuf, MarkPacket> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeByte(packet.action),
                buf -> switch (buf.readUnsignedByte()) {
                    case ACTION_TRANSFORM -> TRANSFORM;
                    case ACTION_SWAP -> TOGGLE_SWAP;
                    default -> MARK;
                });
        private final int action;
        private MarkPacket(int action) {
            this.action = action;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MarkPacket> getPacketType() {
            return PacketTypes.AREA_TELEPORT_SETUP_MARK.get();
        }
    }
}
