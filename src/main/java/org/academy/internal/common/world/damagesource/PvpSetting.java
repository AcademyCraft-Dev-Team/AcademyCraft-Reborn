package org.academy.internal.common.world.damagesource;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player-owned admission policy for AcademyCraft player-to-player interactions.
 *
 * <p>The policy is bilateral: disabling PVP prevents the player from acting on other players and
 * prevents other players from acting on them. Non-player targets are deliberately outside this
 * policy so area skills can continue to affect the rest of their target set.</p>
 */
public final class PvpSetting {
    private static final int FEEDBACK_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayer, FeedbackState> FEEDBACK_STATES = new WeakHashMap<>();
    private static boolean serverInitialized;

    private PvpSetting() {
    }

    public static boolean isPvpEnabled(Player player) {
        return player == null || player.getData(AttachmentTypes.PVP_ENABLED.get());
    }

    public static void setPvpEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.setData(AttachmentTypes.PVP_ENABLED.get(), enabled);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(AttachmentTypes.PVP_ENABLED.get());
        }
    }

    public static void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static ProtectionReason protectionReason(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return ProtectionReason.NONE;
        var playerTarget = target instanceof Player;
        var samePlayer = target == attacker;
        return protectionReason(
                playerTarget,
                samePlayer,
                isPvpEnabled(attacker),
                !(target instanceof Player victim) || isPvpEnabled(victim)
        );
    }

    static ProtectionReason protectionReason(
            boolean playerTarget,
            boolean samePlayer,
            boolean attackerEnabled,
            boolean targetEnabled
    ) {
        if (!playerTarget || samePlayer) return ProtectionReason.NONE;
        if (!attackerEnabled) return ProtectionReason.ATTACKER_DISABLED;
        if (!targetEnabled) return ProtectionReason.TARGET_DISABLED;
        return ProtectionReason.NONE;
    }

    /**
     * Checks a player interaction and provides rate-limited action-bar feedback when it is
     * rejected. Callers should skip only the protected target rather than aborting an area action.
     */
    public static boolean shouldPrevent(Player attacker, LivingEntity target) {
        var reason = protectionReason(attacker, target);
        if (reason == ProtectionReason.NONE) return false;
        if (attacker instanceof ServerPlayer serverPlayer) notifyBlocked(serverPlayer, reason);
        return true;
    }

    public static boolean shouldPrevent(Player attacker, Entity target) {
        return target instanceof LivingEntity living && shouldPrevent(attacker, living);
    }

    public static ServerPlayer resolveAttacker(DamageSource source) {
        if (source == null) return null;
        if (source.getEntity() instanceof ServerPlayer player) return player;
        var direct = source.getDirectEntity();
        if (direct instanceof ServerPlayer player) return player;
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) return player;
        var owner = FriendlyFireSetting.getOwnerEntity(direct);
        return owner instanceof ServerPlayer player ? player : null;
    }

    private static void notifyBlocked(ServerPlayer player, ProtectionReason reason) {
        var now = player.level().getGameTime();
        synchronized (FEEDBACK_STATES) {
            var previous = FEEDBACK_STATES.get(player);
            if (previous != null && previous.reason == reason
                    && now - previous.gameTime < FEEDBACK_COOLDOWN_TICKS) return;
            FEEDBACK_STATES.put(player, new FeedbackState(reason, now));
        }
        player.sendOverlayMessage(Component.translatable(reason.feedbackKey));
    }

    public enum ProtectionReason {
        NONE(""),
        ATTACKER_DISABLED("message.academy.pvp.disabled"),
        TARGET_DISABLED("message.academy.pvp.target_disabled");

        private final String feedbackKey;

        ProtectionReason(String feedbackKey) {
            this.feedbackKey = feedbackKey;
        }
    }

    private record FeedbackState(ProtectionReason reason, long gameTime) {
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void setPvp(SetPacket packet) {
            setPvpEnabled(packet.getPacketListener().getPlayer(), packet.enabled);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                player.syncData(AttachmentTypes.PVP_ENABLED.get());
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
            return PacketTypes.PVP_SET.get();
        }
    }
}
