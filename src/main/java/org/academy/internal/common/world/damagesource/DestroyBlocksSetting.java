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
import org.academy.api.common.ability.Skill;
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

public final class DestroyBlocksSetting {
    public static final String TAG_KEY_DESTROY_BLOCKS = "academy_destroy_blocks_enabled";
    private static final Set<String> BLOCK_DESTRUCTIVE_SKILLS = Set.of(
            "academy:kinetic_energy_applied",
            "academy:plasma_generation",
            "academy:spacial_excision",
            "academy:darkmatter_disassemble",
            "academy:mining_beam",
            "academy:scatter_bomb",
            "academy:particle_wave_cannon",
            "academy:railgun",
            "academy:single_high_speed_electron_beam",
            "academy:disintegrate",
            "academy:laminar_cutter"
    );
    private static final Set<String> GLOBAL_SETTING_INDEPENDENT_SKILLS = Set.of(
            "academy:mining_beam"
    );
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

    public static boolean isSkillDestroyBlocksEnabled(Player player, Skill skill) {
        if (player == null || skill == null) return true;
        return player.getData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get())
                .getOrDefault(skill.getKeyString(), true);
    }

    public static boolean supportsSkillBlockDestruction(Skill skill) {
        return skill != null && BLOCK_DESTRUCTIVE_SKILLS.contains(skill.getKeyString());
    }

    public static boolean canDestroyBlocks(ServerPlayer player, Skill skill) {
        if (!canDestroyBlocksBySkillSetting(player, skill)) return false;
        return usesIndependentBlockDestructionSetting(skill) || canDestroyBlocks(player);
    }

    /**
     * Checks only the per-skill switch shown in the skill settings advanced section.
     */
    public static boolean canDestroyBlocksBySkillSetting(Player player, Skill skill) {
        return supportsSkillBlockDestruction(skill)
                && isSkillDestroyBlocksEnabled(player, skill);
    }

    public static boolean usesIndependentBlockDestructionSetting(Skill skill) {
        return skill != null && usesIndependentBlockDestructionSetting(skill.getKeyString());
    }

    static boolean usesIndependentBlockDestructionSetting(String skillId) {
        return skillId != null && GLOBAL_SETTING_INDEPENDENT_SKILLS.contains(skillId);
    }

    public static void setDestroyBlocksEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), enabled);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get());
        }
    }

    public static void setSkillDestroyBlocksEnabled(Player player, Skill skill, boolean enabled) {
        if (player == null || !supportsSkillBlockDestruction(skill)) return;
        var settings = new HashMap<>(player.getData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get()));
        if (enabled) settings.remove(skill.getKeyString());
        else settings.put(skill.getKeyString(), false);
        player.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(), settings);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get());
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

        @SubscribePacket
        public static void setSkillDestroyBlocks(SetSkillPacket packet) {
            setSkillDestroyBlocksEnabled(packet.getPacketListener().getPlayer(), packet.skill, packet.enabled);
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
            player.syncData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get());
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

    @PacketTarget(ThreadType.SERVER)
    public static final class SetSkillPacket extends Packet<ServerGamePacketListenerImpl, SetSkillPacket> {
        public static final StreamCodec<ByteBuf, SetSkillPacket> CODEC = StreamCodec.composite(
                Skill.STREAM_CODEC,
                packet -> packet.skill,
                ByteBufCodecs.BOOL,
                packet -> packet.enabled,
                SetSkillPacket::new
        );
        private final Skill skill;
        private final boolean enabled;

        public SetSkillPacket(Skill skill, boolean enabled) {
            this.skill = skill;
            this.enabled = enabled;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SetSkillPacket> getPacketType() {
            return PacketTypes.DESTROY_BLOCKS_SKILL_SET.get();
        }
    }
}
