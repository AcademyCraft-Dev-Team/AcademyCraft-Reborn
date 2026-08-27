package org.academy.internal.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Map;
import java.util.Set;

public final class ProficiencySkillSettings {
    public static final String DARKMATTER_SHAPING_AUTO_REPAIR = "darkmatter_shaping.auto_repair";
    public static final String FLASHING_AUTO_ESCAPE = "flashing.auto_escape";
    public static final String MINING_BEAM_HARVEST_MODE = "mining_beam.harvest_mode";
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            DARKMATTER_SHAPING_AUTO_REPAIR,
            FLASHING_AUTO_ESCAPE
    );
    private static final Map<String, Integer> MAX_MODE_VALUES = Map.of(
            MINING_BEAM_HARVEST_MODE, 2
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
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.SKILL_PROFICIENCY_OPTIONS.get());
        }
    }

    public static int getMode(Player player, String option) {
        if (player == null || !MAX_MODE_VALUES.containsKey(option)) return 0;
        return sanitizeMode(option, player.getData(AttachmentTypes.SKILL_PROFICIENCY_MODES.get())
                .getOrDefault(option, 0));
    }

    public static void setMode(Player player, String option, int mode) {
        if (player == null || !MAX_MODE_VALUES.containsKey(option)) return;
        var normalized = sanitizeMode(option, mode);
        var values = new HashMap<>(player.getData(AttachmentTypes.SKILL_PROFICIENCY_MODES.get()));
        if (normalized == 0) values.remove(option);
        else values.put(option, normalized);
        player.setData(AttachmentTypes.SKILL_PROFICIENCY_MODES.get(), values);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.SKILL_PROFICIENCY_MODES.get());
        }
    }

    static int sanitizeMode(String option, int mode) {
        return Math.clamp(mode, 0, MAX_MODE_VALUES.getOrDefault(option, 0));
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

        @SubscribePacket
        public static void setMode(SetModePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (MINING_BEAM_HARVEST_MODE.equals(packet.option)
                    && !Skills.MINING_BEAM.get().hasProficiencyMilestone(player, 3)) {
                return;
            }
            ProficiencySkillSettings.setMode(player, packet.option, packet.mode);
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

    @PacketTarget(ThreadType.SERVER)
    public static final class SetModePacket extends Packet<ServerGamePacketListenerImpl, SetModePacket> {
        public static final StreamCodec<ByteBuf, SetModePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                packet -> packet.option,
                ByteBufCodecs.VAR_INT,
                packet -> packet.mode,
                SetModePacket::new
        );
        private final String option;
        private final int mode;

        public SetModePacket(String option, int mode) {
            this.option = option;
            this.mode = mode;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetModePacket> getPacketType() {
            return PacketTypes.PROFICIENCY_SKILL_MODE_SET.get();
        }
    }
}
