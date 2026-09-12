package org.academy.internal.server.music;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.internal.common.music.QueueSnapshot;
import org.academy.internal.common.music.SharedPlaybackChannel;
import org.academy.internal.common.music.SharedTrackEntry;
import org.academy.internal.common.music.SkipVoteSession;
import org.academy.internal.common.network.MusicJukeboxPackets;
import org.academy.internal.server.config.MusicConfig;
import org.misaka.MisakaNetworkServer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全服点播服务端管理器：公共队列 + 投票切歌 + 预设歌单兜底。
 * 客户端可订阅/静音；静音者不接收同步包也不参与投票喵。
 */
@EventBusSubscriber
public final class ServerJukeboxManager {
    private static final int HEARTBEAT_INTERVAL_TICKS = 200;

    private static final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();
    private static final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private static final SharedPlaybackChannel channel = new SharedPlaybackChannel();
    private static SkipVoteSession votes;
    private static volatile boolean playingPreset;

    private static MinecraftServer server;
    private static MusicConfig.JukeboxSettings settings = new MusicConfig.JukeboxSettings();
    private static boolean initialized;
    private static long lastHeartbeatTick = Long.MIN_VALUE;

    private ServerJukeboxManager() {
    }

    public static void initServer(MinecraftServer minecraftServer, MusicConfig config) {
        server = minecraftServer;
        settings = config.jukebox;
        channel.clear();
        votes = null;
        playingPreset = false;
        requestCooldowns.clear();
        subscribers.clear();
        lastHeartbeatTick = Long.MIN_VALUE;
        PresetPlaylistStore.getInstance().load();
        if (!initialized) {
            initialized = true;
            MusicJukeboxPackets.initServer();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || server == null) return;
        subscribers.add(player.getUUID());
        sendStateTo(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        subscribers.remove(player.getUUID());
        requestCooldowns.remove(player.getUUID());
        if (votes != null && votes.removeVote(player.getUUID()) && votes.voteCount() == 0) {
            votes = null;
        }
    }

    public static void tick(MinecraftServer minecraftServer) {
        if (!settings.enabled) return;
        var now = minecraftServer.overworld().getGameTime();
        var changed = channel.advanceIfEnded(now);
        if (changed) {
            votes = null;
            playingPreset = false;
        }
        if (channel.timeline().isEmpty() && PresetPlaylistStore.getInstance().isEnabled()) {
            playNextPreset(now);
            changed = true;
        }
        if (votes != null && votes.isExpired(now)) {
            votes = null;
            changed = true;
        }
        if (changed) {
            broadcast();
        }
        if (minecraftServer.getTickCount() - lastHeartbeatTick >= HEARTBEAT_INTERVAL_TICKS) {
            lastHeartbeatTick = minecraftServer.getTickCount();
            broadcast();
        }
    }

    public static void handleAction(MusicJukeboxPackets.ActionPacket packet) {
        var player = packet.getPacketListener().getPlayer();
        switch (packet.action()) {
            case REQUEST -> {
                if (packet.entry() != null) request(player, packet.entry());
            }
            case VOTE_SKIP -> voteSkip(player);
            case SUBSCRIBE -> setSubscribed(player, packet.flag());
            case REQUEST_STATE -> sendStateTo(player);
        }
    }

    public static void request(ServerPlayer player, SharedTrackEntry rawEntry) {
        if (!settings.enabled) {
            sendError(player, "message.academy.jukebox.disabled");
            return;
        }
        var entry = MusicRoomManager.sanitizeEntry(rawEntry);
        if (entry == null) {
            sendError(player, "message.academy.jukebox.invalid_track");
            return;
        }
        if (entry.durationSeconds() > settings.maxTrackDurationSeconds) {
            sendError(player, "message.academy.jukebox.track_too_long");
            return;
        }
        var now = System.currentTimeMillis();
        var lastRequest = requestCooldowns.getOrDefault(player.getUUID(), 0L);
        if (now - lastRequest < settings.requestCooldownSeconds * 1000L) {
            var remaining = (settings.requestCooldownSeconds * 1000L - (now - lastRequest) + 999) / 1000;
            player.sendSystemMessage(Component.translatable(
                    "message.academy.jukebox.cooldown", remaining
            ).withStyle(ChatFormatting.RED));
            return;
        }

        boolean wasPlayingPreset;
        synchronized (channel) {
            if (countPlayerPending(player.getGameProfile().name()) >= settings.perPlayerLimit) {
                sendError(player, "message.academy.jukebox.player_limit");
                return;
            }
            if (channel.queueSize() >= settings.maxQueue) {
                sendError(player, "message.academy.jukebox.queue_full");
                return;
            }
            if (isDuplicate(entry)) {
                sendError(player, "message.academy.jukebox.duplicate");
                return;
            }
            wasPlayingPreset = playingPreset;
        }
        requestCooldowns.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatable(
                "message.academy.jukebox.requested", entry.title()
        ).withStyle(ChatFormatting.GOLD));

        synchronized (channel) {
            channel.enqueue(new QueueSnapshot.QueueEntry(entry, player.getGameProfile().name()));
            if (channel.timeline().isEmpty()) {
                channel.skipToNext(gameTime());
                playingPreset = false;
            } else if (wasPlayingPreset && settings.interruptPresetOnRequest) {
                channel.skipToNext(gameTime());
                playingPreset = false;
            }
        }
        broadcast();
    }

    public static void voteSkip(ServerPlayer player) {
        if (!settings.enabled) return;
        if (!subscribers.contains(player.getUUID())) {
            sendError(player, "message.academy.jukebox.muted_no_vote");
            return;
        }
        var now = gameTime();
        if (votes == null) {
            votes = new SkipVoteSession(now, settings.voteWindowSeconds * 20L,
                    player.getUUID(), player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("message.academy.jukebox.vote_started"));
            broadcast();
            return;
        }
        if (!votes.addVote(player.getUUID(), player.getGameProfile().name())) {
            sendError(player, "message.academy.jukebox.already_voted");
            return;
        }
        var threshold = voteThreshold();
        if (votes.voteCount() >= threshold) {
            votes = null;
            synchronized (channel) {
                channel.skipToNext(gameTime());
                playingPreset = false;
            }
            announce(Component.translatable("message.academy.jukebox.vote_passed"));
        } else {
            announce(Component.translatable(
                    "message.academy.jukebox.vote_progress", votes.voteCount(), threshold
            ));
        }
        broadcast();
    }

    public static void setSubscribed(ServerPlayer player, boolean subscribed) {
        if (subscribed) {
            subscribers.add(player.getUUID());
            sendStateTo(player);
        } else {
            subscribers.remove(player.getUUID());
        }
    }

    public static boolean isSubscribed(UUID uuid) {
        return subscribers.contains(uuid);
    }

    /**
     * 只读查询：全服点播当前播放时间线（服务端空闲时为空）。
     * 供诊断/测试与后续精密操作接口查询点播状态喵。
     */
    public static java.util.Optional<org.academy.internal.common.music.PlaybackTimeline> timeline() {
        return channel.timeline();
    }

    /**
     * 只读查询：全服点播待播队列快照喵。
     */
    public static QueueSnapshot queue() {
        return channel.queueSnapshot();
    }

    public static void forceSkip() {
        votes = null;
        synchronized (channel) {
            channel.skipToNext(gameTime());
            playingPreset = false;
        }
        broadcast();
    }

    public static void clearQueue() {
        synchronized (channel) {
            // 仅保留当前曲目：重建通道，队列清空
            var current = channel.currentQueueEntry().orElse(null);
            channel.clear();
            if (current != null) {
                channel.playNow(current, gameTime());
            }
        }
        votes = null;
        broadcast();
    }

    public static void sendStateTo(ServerPlayer player) {
        MisakaNetworkServer.send(player, buildSyncPacket());
    }

    private static void playNextPreset(long now) {
        var entry = PresetPlaylistStore.getInstance().next();
        if (entry == null) return;
        synchronized (channel) {
            channel.playNow(new QueueSnapshot.QueueEntry(entry, ""), now);
            playingPreset = true;
        }
    }

    private static int countPlayerPending(String playerName) {
        var current = channel.currentQueueEntry().map(QueueSnapshot.QueueEntry::requesterName).orElse("");
        var pending = 0;
        if (current.equals(playerName)) pending++;
        for (var queueEntry : channel.queueSnapshot().entries()) {
            if (queueEntry.requesterName().equals(playerName)) pending++;
        }
        return pending;
    }

    private static boolean isDuplicate(SharedTrackEntry entry) {
        var current = channel.currentEntry().orElse(null);
        if (current != null && current.sameTrack(entry.provider(), entry.trackId())) return true;
        return channel.queueSnapshot().entries().stream()
                .anyMatch(queueEntry -> queueEntry.entry().sameTrack(entry.provider(), entry.trackId()));
    }

    private static int voteThreshold() {
        if (settings.voteMode != null
                && settings.voteMode.toUpperCase(Locale.ROOT).startsWith("FIXED")) {
            return Math.max(2, settings.voteFixedCount);
        }
        var online = 0;
        for (var uuid : subscribers) {
            if (server.getPlayerList().getPlayer(uuid) != null) online++;
        }
        // 单人在线时一票即过，多人时需过半（至少2票）
        return Math.max(online <= 1 ? 1 : 2, (online + 1) / 2);
    }

    private static MusicJukeboxPackets.SyncPacket buildSyncPacket() {
        var now = gameTime();
        var stored = channel.timeline().orElse(null);
        // 广播前按当前tick重锚，接收端据此直接得到正确曲内进度，避免误从头播放喵.
        var timeline = stored == null ? null : stored.reanchored(now);
        return new MusicJukeboxPackets.SyncPacket(
                settings.enabled,
                channel.timeline().isEmpty()
                        ? MusicJukeboxPackets.MODE_IDLE
                        : (playingPreset ? MusicJukeboxPackets.MODE_PRESET : MusicJukeboxPackets.MODE_JUKEBOX),
                timeline,
                channel.queueSnapshot(),
                votes != null,
                votes == null ? 0 : votes.voteCount(),
                voteThreshold(),
                votes == null ? 0L : votes.endsAtGameTime(),
                now
        );
    }

    private static void broadcast() {
        if (server == null) return;
        var packet = buildSyncPacket();
        for (var uuid : subscribers) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) MisakaNetworkServer.send(player, packet);
        }
    }

    private static void announce(Component message) {
        if (server == null) return;
        for (var uuid : subscribers) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private static long gameTime() {
        return server == null ? 0L : server.overworld().getGameTime();
    }

    private static void sendError(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }
}
