package org.academy.internal.common.world.damagesource;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

public final class FriendlyFireSetting {
    public static final String TAG_KEY_FRIENDLY_FIRE = "academy_friendly_fire_enabled";
    private static final String CONFLUENCE_RAINBOW_SHEEP = "confluence:rainbow_sheep";
    private static final String TERRA_ENTITY_ABSTRACT_NPC =
            "org.confluence.terraentity.entity.npc.AbstractTerraNPC";
    private static boolean serverInitialized;

    private FriendlyFireSetting() {
    }

    public static boolean isFriendlyFireEnabled(Player player) {
        return player == null || player.getData(AttachmentTypes.FRIENDLY_FIRE_ENABLED.get());
    }

    public static void setFriendlyFireEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.setData(AttachmentTypes.FRIENDLY_FIRE_ENABLED.get(), enabled);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.FRIENDLY_FIRE_ENABLED.get());
        }
    }

    public static void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static boolean shouldPrevent(Player attacker, LivingEntity target) {
        if (attacker == null || target == null || isFriendlyFireEnabled(attacker)) return false;
        if (target instanceof Player victim && victim != attacker
                && TeamRelations.areTeammates(attacker, victim)) return true;
        var owner = getOwnerEntity(target);
        if (owner == attacker || TeamRelations.areTeammates(attacker, owner)) return true;
        if (isConfluenceFriendly(target)) return true;
        var ownerId = getOwnerUuid(target);
        return ownerId != null && ownerId.equals(attacker.getUUID());
    }

    private static boolean isConfluenceFriendly(LivingEntity target) {
        if (CONFLUENCE_RAINBOW_SHEEP.equals(
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString())) return true;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            if (TERRA_ENTITY_ABSTRACT_NPC.equals(type.getName())) return true;
        }
        return false;
    }

    static UUID getOwnerUuid(Entity entity) {
        var value = invoke(entity, "getOwnerUUID");
        if (value instanceof UUID uuid) return uuid;
        var owner = getOwnerEntity(entity);
        return owner == null ? null : owner.getUUID();
    }

    static Entity getOwnerEntity(Entity entity) {
        var value = invoke(entity, "getOwner");
        return value instanceof Entity owner ? owner : null;
    }

    private static Object invoke(Entity entity, String name) {
        if (entity == null) return null;
        try {
            var method = entity.getClass().getMethod(name);
            return method.invoke(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void setFriendlyFire(SetPacket packet) {
            setFriendlyFireEnabled(packet.getPacketListener().getPlayer(), packet.enabled);
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
            if (legacyData.contains(TAG_KEY_FRIENDLY_FIRE)) {
                setFriendlyFireEnabled(
                        player,
                        legacyData.getBoolean(TAG_KEY_FRIENDLY_FIRE).orElse(true)
                );
                legacyData.remove(TAG_KEY_FRIENDLY_FIRE);
            } else {
                player.syncData(AttachmentTypes.FRIENDLY_FIRE_ENABLED.get());
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
            return PacketTypes.FRIENDLY_FIRE_SET.get();
        }
    }
}
