package org.academy.internal.common.world.damagesource;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.pvp.PvpCooldownData;
import org.misaka.MisakaNetworkClient;
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
    public static final int SWITCH_COOLDOWN_TICKS = 20 * 60;
    private static final int FEEDBACK_COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayer, FeedbackState> FEEDBACK_STATES = new WeakHashMap<>();
    private static volatile boolean clientPvpEnabled = true;
    private static volatile boolean clientPvpStateKnown;
    private static boolean serverInitialized;

    private PvpSetting() {
    }

    public static boolean isPvpEnabled(Player player) {
        return player == null || player.getData(AttachmentTypes.PVP_ENABLED.get());
    }

    private static void setPvpEnabled(ServerPlayer player, boolean enabled) {
        player.setData(AttachmentTypes.PVP_ENABLED.get(), enabled);
    }

    public static void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static boolean clientPvpEnabled(boolean fallback) {
        return clientPvpStateKnown ? clientPvpEnabled : fallback;
    }

    public static void expectClientPvpEnabled(boolean enabled) {
        clientPvpEnabled = enabled;
        clientPvpStateKnown = true;
    }

    private static void resetClientState() {
        clientPvpEnabled = true;
        clientPvpStateKnown = false;
    }

    public static int remainingSwitchCooldownTicks(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return 0;
        return PvpCooldownData.get(player.level().getServer()).remainingTicks(player.getUUID());
    }

    public static void startSwitchCooldown(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return;
        PvpCooldownData.get(player.level().getServer()).startOrRefresh(
                player.getUUID(), SWITCH_COOLDOWN_TICKS);
    }

    public static void recordSkillDamage(ServerPlayer attacker, LivingEntity target, float inflictedDamage) {
        if (attacker == null || target == null || !shouldStartCooldown(
                target instanceof Player, target == attacker, inflictedDamage)) return;
        startSwitchCooldown(attacker);
    }

    static boolean shouldStartCooldown(boolean playerTarget, boolean samePlayer, float inflictedDamage) {
        return playerTarget && !samePlayer
                && inflictedDamage > 0.0f && Float.isFinite(inflictedDamage);
    }

    public static ChangeResult trySetPvpEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return ChangeResult.UNCHANGED;
        var current = isPvpEnabled(player);
        var remainingTicks = remainingSwitchCooldownTicks(player);
        var result = changeResult(current, enabled, remainingTicks);
        if (result == ChangeResult.APPLIED) {
            setPvpEnabled(player, enabled);
            startSwitchCooldown(player);
        } else if (result == ChangeResult.COOLDOWN) {
            var remainingSeconds = Math.max(1, (remainingTicks + 19) / 20);
            player.sendOverlayMessage(Component.translatable(
                    "message.academy.pvp.switch_cooldown", remainingSeconds));
        }
        syncState(player);
        return result;
    }

    static ChangeResult changeResult(boolean current, boolean requested, int remainingTicks) {
        if (current == requested) return ChangeResult.UNCHANGED;
        return remainingTicks > 0 ? ChangeResult.COOLDOWN : ChangeResult.APPLIED;
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

    public enum ChangeResult {
        APPLIED,
        UNCHANGED,
        COOLDOWN
    }

    private static void syncState(ServerPlayer player) {
        player.syncData(AttachmentTypes.PVP_ENABLED.get());
        MisakaNetworkServer.send(player, new StatePacket(isPvpEnabled(player)));
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void setPvp(SetPacket packet) {
            trySetPvpEnabled(packet.getPacketListener().getPlayer(), packet.enabled);
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void syncPvp(StatePacket packet) {
            clientPvpEnabled = packet.enabled;
            clientPvpStateKnown = true;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            resetClientState();
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                syncState(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || player.level().getServer() == null) return;
            PvpCooldownData.get(player.level().getServer()).tickOnline(player.getUUID());
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

    @PacketTarget(ThreadType.CLIENT)
    public static final class StatePacket extends Packet<ClientPacketListener, StatePacket> {
        public static final StreamCodec<ByteBuf, StatePacket> CODEC =
                ByteBufCodecs.BOOL.map(StatePacket::new, packet -> packet.enabled);
        private final boolean enabled;

        public StatePacket(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public PacketType<ClientPacketListener, StatePacket> getPacketType() {
            return PacketTypes.PVP_STATE.get();
        }
    }
}
