package org.academy.internal.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Set;

public final class ProficiencySkillSettings {
    public static final String DARKMATTER_SHAPING_AUTO_REPAIR = "darkmatter_shaping.auto_repair";
    public static final String FLASHING_AUTO_ESCAPE = "flashing.auto_escape";
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            DARKMATTER_SHAPING_AUTO_REPAIR,
            FLASHING_AUTO_ESCAPE
    );
    private static boolean serverInitialized;

    private ProficiencySkillSettings() {
    }

    public static boolean isEnabled(Player player, String option) {
        if (player == null || !ALLOWED_OPTIONS.contains(option)) return false;
        return player.getData(AttachmentTypes.SKILL_PROFICIENCY_OPTIONS.get())
                .getOrDefault(option, true);
    }

    public static void setEnabled(Player player, String option, boolean enabled) {
        if (player == null || !ALLOWED_OPTIONS.contains(option)) return;
        var values = new HashMap<>(player.getData(AttachmentTypes.SKILL_PROFICIENCY_OPTIONS.get()));
        if (enabled) values.remove(option);
        else values.put(option, false);
        player.setData(AttachmentTypes.SKILL_PROFICIENCY_OPTIONS.get(), values);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.SKILL_PROFICIENCY_OPTIONS.get());
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
        public static void set(SetPacket packet) {
            setEnabled(packet.getPacketListener().getPlayer(), packet.option, packet.enabled);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SetPacket extends Packet<ServerGamePacketListenerImpl, SetPacket> {
        public static final StreamCodec<ByteBuf, SetPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                packet -> packet.option,
                ByteBufCodecs.BOOL,
                packet -> packet.enabled,
                SetPacket::new
        );
        private final String option;
        private final boolean enabled;

        public SetPacket(String option, boolean enabled) {
            this.option = option;
            this.enabled = enabled;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetPacket> getPacketType() {
            return PacketTypes.PROFICIENCY_SKILL_OPTION_SET.get();
        }
    }
}
