package org.academy.internal.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

public final class ProficiencySkillSettings {
    public static final String MINING_BEAM_SMELTING = "mining_beam.smelting";
    public static final String DARKMATTER_SHAPING_AUTO_REPAIR = "darkmatter_shaping.auto_repair";
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            MINING_BEAM_SMELTING,
            DARKMATTER_SHAPING_AUTO_REPAIR
    );
    private static boolean serverInitialized;
    private static boolean clientInitialized;

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

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        registerToggle(
                Skills.MINING_BEAM.get(),
                "smelting",
                "app.academy.skill_settings.advanced.mining_beam_smelting",
                MINING_BEAM_SMELTING
        );
        registerToggle(
                Skills.DARKMATTER_SHAPING.get(),
                "auto_repair",
                "app.academy.skill_settings.advanced.darkmatter_auto_repair",
                DARKMATTER_SHAPING_AUTO_REPAIR
        );
    }

    private static void registerToggle(
            org.academy.api.common.ability.Skill skill,
            String id,
            String labelKey,
            String option
    ) {
        SkillSettingsRegistry.register(
                skill,
                new SkillSettingsRegistry.Module(
                        "proficiency",
                        "",
                        List.of(new SkillSettingsRegistry.Toggle(
                                id,
                                labelKey,
                                () -> isEnabled(Minecraft.getInstance().player, option),
                                enabled -> {
                                    var player = Minecraft.getInstance().player;
                                    if (player == null) return;
                                    setEnabled(player, option, enabled);
                                    MisakaNetworkClient.send(new SetPacket(option, enabled));
                                }
                        ))
                )
        );
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
