package org.academy.internal.server.music;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.PlaybackTimeline;
import org.academy.internal.common.music.QueueSnapshot;
import org.academy.internal.common.music.SharedPlaybackChannel;
import org.academy.internal.common.music.SharedTrackEntry;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.network.MusicRoomPackets;
import org.academy.internal.server.config.MusicConfig;
import org.academy.internal.server.util.ChatConfirmLinks;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public final class MusicRoomManager {
    private static final int MAX_QUEUE_PER_ROOM = 64;
    private static final long TOKEN_TTL_MILLIS = 60_000L;
    private static final int HEARTBEAT_INTERVAL_TICKS = 200;
    private static final int AUTOSAVE_INTERVAL_TICKS = 6000;
    private static final int ROOM_NAME_MAX_LENGTH = 24;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String COMMAND_ROOT = "/academy music room ";

    private static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_ROOMS = new ConcurrentHashMap<>();
    private static final Map<String, PendingAction> PENDING_ACTIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static @Nullable MinecraftServer server;
    private static volatile boolean roomEnabled = true;
    private static volatile int maxRooms = 16;
    private static volatile int maxMembers = 16;
    private static long lastHeartbeatTick = Long.MIN_VALUE;
    private static long lastAutosaveTick = Long.MIN_VALUE;
    private static volatile boolean shuttingDown = false;

    private MusicRoomManager() {
    }

    public static void initServer(MinecraftServer minecraftServer, MusicConfig config) {
        server = minecraftServer;
        roomEnabled = config.room.enabled;
        maxRooms = config.room.maxRooms;
        maxMembers = config.room.maxMembers;
        ROOMS.clear();
        PLAYER_ROOMS.clear();
        PENDING_ACTIONS.clear();
        lastHeartbeatTick = Long.MIN_VALUE;
        lastAutosaveTick = Long.MIN_VALUE;
        shuttingDown = false;
        MusicRoomPackets.initServer();
        restoreRooms();
    }

    public static void saveState() {
        persist(true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (server == null || shuttingDown) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var code = PLAYER_ROOMS.get(player.getUUID());
        if (code == null) return;
        var room = ROOMS.get(code);
        if (room == null) {
            PLAYER_ROOMS.remove(player.getUUID());
            return;
        }
        boolean hostTransferred;
        room.members.put(player.getUUID(), player.getGameProfile().name());
        hostTransferred = !room.host.equals(player.getUUID())
                && server.getPlayerList().getPlayer(room.host) == null;
        if (hostTransferred) {
            room.host = player.getUUID();
        }
        if (hostTransferred) {
            announce(room, Component.translatable(
                    "message.academy.music_room.host_transferred", player.getGameProfile().name()
            ).withStyle(ChatFormatting.GOLD));
        }
        broadcastRoom(room);
        broadcastRoomList();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server != null && !server.isRunning()) {
            if (!shuttingDown) saveState();
            return;
        }
        if (shuttingDown) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        leaveInternal(player, true);
    }

    public static void tick(MinecraftServer minecraftServer) {
        var now = cleanupTokens();
        for (var room : ROOMS.values()) {
            room.channel.advanceIfEnded(now);
            if (room.channel.timeline().isEmpty()) {
                room.channel.skipToNext(now);
            }
        }
        if (minecraftServer.getTickCount() - lastHeartbeatTick >= HEARTBEAT_INTERVAL_TICKS) {
            lastHeartbeatTick = minecraftServer.getTickCount();
            for (var room : ROOMS.values()) {
                broadcastRoom(room);
            }
        }
        if (minecraftServer.getTickCount() - lastAutosaveTick >= AUTOSAVE_INTERVAL_TICKS) {
            lastAutosaveTick = minecraftServer.getTickCount();
            persist(false);
        }
    }

    public static void handleAction(MusicRoomPackets.ActionPacket packet) {
        var player = packet.getPacketListener().getPlayer();
        var action = packet.action();
        switch (action) {
            case CREATE -> create(player, packet.text());
            case RENAME -> rename(player, packet.text());
            case APPLY -> apply(player, packet.roomCode());
            case INVITE -> invite(player, packet.targetName());
            case ACCEPT -> accept(player, packet.token());
            case REJECT -> reject(player, packet.token());
            case LEAVE -> leave(player);
            case KICK -> kick(player, packet.targetName());
            case PLAY -> {
                if (packet.entry() != null) play(player, packet.entry());
            }
            case PAUSE -> pause(player);
            case RESUME -> resume(player);
            case SEEK -> seek(player, packet.seekSeconds());
            case NEXT -> next(player);
            case PREVIOUS -> previous(player);
            case QUEUE_ADD -> {
                if (packet.entry() != null) queueAdd(player, packet.entry());
            }
            case QUEUE_REMOVE -> queueRemove(player, packet.queueIndex());
            case REQUEST_LIST -> {
                MisakaNetworkServer.send(player, buildListPacket());
                pushPendingNotices(player.getUUID());
                var room = roomOf(player);
                if (room != null) broadcastRoom(room);
            }
        }
    }

    public static void create(ServerPlayer player, String rawName) {
        if (roomOf(player) != null) {
            sendError(player, "message.academy.music_room.already_in_room");
            return;
        }
        if (!roomEnabled || ROOMS.size() >= maxRooms) {
            sendError(player, "message.academy.music_room.rooms_full");
            return;
        }
        var name = sanitizeName(rawName);
        if (name.isEmpty()) {
            sendError(player, "message.academy.music_room.name_invalid");
            return;
        }
        var room = newRoom(player, name);
        ROOMS.put(room.code, room);
        PLAYER_ROOMS.put(player.getUUID(), room.code);
        broadcastRoom(room);
        broadcastRoomList();
        player.sendSystemMessage(Component.translatable(
                "message.academy.music_room.created", name, room.code
        ).withStyle(ChatFormatting.GOLD));
    }

    public static void rename(ServerPlayer player, String rawName) {
        var room = requireHostRoom(player, "rename");
        if (room == null) return;
        var name = sanitizeName(rawName);
        if (name.isEmpty()) {
            sendError(player, "message.academy.music_room.name_invalid");
            return;
        }
        room.name = name;
        broadcastRoom(room);
        broadcastRoomList();
    }

    public static void apply(ServerPlayer player, String rawCode) {
        if (server == null) return;
        if (roomOf(player) != null) {
            sendError(player, "message.academy.music_room.already_in_room");
            return;
        }
        var room = ROOMS.get(normalizeCode(rawCode));
        if (room == null) {
            sendError(player, "message.academy.music_room.room_not_found");
            return;
        }
        ServerPlayer host;
        host = server.getPlayerList().getPlayer(room.host);
        if (host == null) {
            sendError(player, "message.academy.music_room.host_offline");
            return;
        }
        var token = newToken(new PendingAction(
                PendingAction.Kind.APPLY, room.code, player.getUUID(), player.getGameProfile().name(),
                room.host, System.currentTimeMillis()
        ));
        var applicantName = player.getGameProfile().name();
        var roomName = room.name;
        ChatConfirmLinks.sendChoice(
                host,
                Component.translatable("message.academy.music_room.apply_prompt", applicantName, roomName),
                label("ui.academy.music_room.accept"),
                COMMAND_ROOT + "accept " + token,
                label("ui.academy.music_room.reject"),
                COMMAND_ROOT + "reject " + token
        );
        pushPendingNotices(room.host);
        player.sendSystemMessage(Component.translatable(
                "message.academy.music_room.apply_sent", host.getGameProfile().name()
        ));
    }

    public static void invite(ServerPlayer player, String targetName) {
        if (server == null) return;
        var room = requireHostRoom(player, "invite");
        if (room == null) return;
        var target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            sendError(player, "message.academy.music_room.player_not_found");
            return;
        }
        if (target.getUUID().equals(player.getUUID())) {
            sendError(player, "message.academy.music_room.invite_self");
            return;
        }
        if (roomOf(target) != null) {
            sendError(player, "message.academy.music_room.target_in_room");
            return;
        }
        var token = newToken(new PendingAction(
                PendingAction.Kind.INVITE, room.code, target.getUUID(), target.getGameProfile().name(),
                player.getUUID(), System.currentTimeMillis()
        ));
        ChatConfirmLinks.sendChoice(
                target,
                Component.translatable(
                        "message.academy.music_room.invite_prompt",
                        player.getGameProfile().name(), room.name
                ),
                label("ui.academy.music_room.accept"),
                COMMAND_ROOT + "accept " + token,
                label("ui.academy.music_room.reject"),
                COMMAND_ROOT + "reject " + token
        );
        pushPendingNotices(target.getUUID());
        player.sendSystemMessage(Component.translatable(
                "message.academy.music_room.invite_sent", target.getGameProfile().name()
        ));
    }

    public static void accept(ServerPlayer player, String token) {
        if (server == null) return;
        var pending = PENDING_ACTIONS.remove(token);
        if (pending == null || isExpired(pending)) {
            sendError(player, "message.academy.music_room.token_invalid");
            pushPendingNotices(player.getUUID());
            return;
        }
        pushPendingNotices(pending.receiverUuid());
        var room = ROOMS.get(pending.roomCode());
        if (room == null) {
            sendError(player, "message.academy.music_room.room_not_found");
            return;
        }
        switch (pending.kind()) {
            case APPLY -> {
                ServerPlayer clicker = player;
                if (!clicker.getUUID().equals(room.host)) {
                    sendError(clicker, "message.academy.music_room.not_host");
                    return;
                }
                var applicant = server.getPlayerList().getPlayer(pending.subjectUuid());
                if (applicant == null) {
                    sendError(clicker, "message.academy.music_room.player_not_found");
                    return;
                }
                addMember(room, applicant);
            }
            case INVITE -> {
                if (!player.getUUID().equals(pending.subjectUuid())) {
                    sendError(player, "message.academy.music_room.token_invalid");
                    return;
                }
                addMember(room, player);
            }
        }
    }

    public static void reject(ServerPlayer player, String token) {
        if (server == null) return;
        var pending = PENDING_ACTIONS.remove(token);
        if (pending == null || isExpired(pending)) {
            sendError(player, "message.academy.music_room.token_invalid");
            pushPendingNotices(player.getUUID());
            return;
        }
        pushPendingNotices(pending.receiverUuid());
        if (pending.kind() == PendingAction.Kind.APPLY) {
            var applicant = server.getPlayerList().getPlayer(pending.subjectUuid());
            if (applicant != null) {
                applicant.sendSystemMessage(Component.translatable(
                        "message.academy.music_room.apply_rejected"
                ).withStyle(ChatFormatting.RED));
            }
        } else {
            var room = ROOMS.get(pending.roomCode());
            if (room != null) {
                var inviter = server.getPlayerList().getPlayer(room.host);
                if (inviter != null) {
                    inviter.sendSystemMessage(Component.translatable(
                            "message.academy.music_room.invite_declined", player.getGameProfile().name()
                    ).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    public static void leave(ServerPlayer player) {
        leaveInternal(player, false);
    }

    private static void leaveInternal(ServerPlayer player, boolean offline) {
        var code = PLAYER_ROOMS.remove(player.getUUID());
        if (code == null) return;
        var room = ROOMS.get(code);
        if (room == null) return;
        String newHostName = null;
        boolean disbanded = false;
        room.members.remove(player.getUUID());
        if (room.members.isEmpty()) {
            disbanded = true;
        } else if (room.host.equals(player.getUUID())) {
            var nextHost = room.members.keySet().iterator().next();
            room.host = nextHost;
            newHostName = room.members.get(nextHost);
        }
        announce(room, offline
                ? Component.translatable("message.academy.music_room.member_offline", player.getGameProfile().name())
                : Component.translatable("message.academy.music_room.member_left", player.getGameProfile().name()));
        if (disbanded) {
            ROOMS.remove(code);
            AcademyCraft.LOGGER.debug("Music room {} disbanded", code);
        } else {
            if (newHostName != null) {
                announce(room, Component.translatable(
                        "message.academy.music_room.host_transferred", newHostName
                ).withStyle(ChatFormatting.GOLD));
            }
            broadcastRoom(room);
        }
        broadcastRoomList();
        if (!offline) {
            MisakaNetworkServer.send(player, MusicRoomPackets.SyncPacket.left());
            player.sendSystemMessage(Component.translatable("message.academy.music_room.left"));
        }
    }

    public static void kick(ServerPlayer player, String targetName) {
        if (server == null) return;
        var room = requireHostRoom(player, "kick");
        if (room == null) return;
        ServerPlayer target;
        UUID targetUuid;
        targetUuid = room.members.entrySet().stream()
                .filter(entry -> entry.getValue().equals(targetName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (targetUuid == null) {
            sendError(player, "message.academy.music_room.player_not_in_room");
            return;
        }
        if (targetUuid.equals(room.host)) {
            sendError(player, "message.academy.music_room.cannot_kick_host");
            return;
        }
        target = server.getPlayerList().getPlayer(targetUuid);
        room.members.remove(targetUuid);
        PLAYER_ROOMS.remove(targetUuid);
        if (target != null) {
            MisakaNetworkServer.send(target, MusicRoomPackets.SyncPacket.left());
            target.sendSystemMessage(Component.translatable("message.academy.music_room.kicked")
                    .withStyle(ChatFormatting.RED));
        }
        announce(room, Component.translatable("message.academy.music_room.member_left", targetName));
        broadcastRoom(room);
        broadcastRoomList();
    }

    public static void play(ServerPlayer player, SharedTrackEntry rawEntry) {
        var room = requireRoom(player, "play");
        if (room == null) return;
        var entry = sanitizeEntry(rawEntry);
        if (entry == null) {
            sendError(player, "message.academy.music_room.invalid_track");
            return;
        }
        room.channel.playNow(new QueueSnapshot.QueueEntry(entry, player.getGameProfile().name()), gameTime());
        broadcastRoom(room);
    }

    public static void pause(ServerPlayer player) {
        var room = requireRoom(player, "pause");
        if (room == null) return;
        room.channel.pause(gameTime());
        broadcastRoom(room);
    }

    public static void resume(ServerPlayer player) {
        var room = requireRoom(player, "resume");
        if (room == null) return;
        room.channel.resume(gameTime());
        broadcastRoom(room);
    }

    public static void seek(ServerPlayer player, float seconds) {
        var room = requireRoom(player, "seek");
        if (room == null) return;
        if (!room.channel.seek(gameTime(), seconds)) {
            sendError(player, "message.academy.music_room.nothing_playing");
            return;
        }
        broadcastRoom(room);
    }

    public static void next(ServerPlayer player) {
        var room = requireRoom(player, "next");
        if (room == null) return;
        room.channel.skipToNext(gameTime());
        broadcastRoom(room);
    }

    public static void previous(ServerPlayer player) {
        var room = requireRoom(player, "previous");
        if (room == null) return;
        room.channel.skipToPrevious(gameTime());
        broadcastRoom(room);
    }

    public static void queueAdd(ServerPlayer player, SharedTrackEntry rawEntry) {
        var room = requireRoom(player, "queue_add");
        if (room == null) return;
        var entry = sanitizeEntry(rawEntry);
        if (entry == null) {
            sendError(player, "message.academy.music_room.invalid_track");
            return;
        }
        if (room.channel.queueSize() >= MAX_QUEUE_PER_ROOM) {
            sendError(player, "message.academy.music_room.queue_full");
            return;
        }
        room.channel.enqueue(new QueueSnapshot.QueueEntry(entry, player.getGameProfile().name()));
        if (room.channel.timeline().isEmpty()) {
            room.channel.skipToNext(gameTime());
        }
        broadcastRoom(room);
    }

    public static void queueRemove(ServerPlayer player, int index) {
        var room = requireRoom(player, "queue_remove");
        if (room == null) return;
        room.channel.removeQueueIndex(index);
        broadcastRoom(room);
    }

    public static Optional<PlaybackTimeline> timelineOf(ServerPlayer player) {
        var room = roomOf(player);
        return room == null ? Optional.empty() : room.channel.timeline();
    }

    public static QueueSnapshot queueOf(ServerPlayer player) {
        var room = roomOf(player);
        return room == null ? new QueueSnapshot(List.of(), 0) : room.channel.queueSnapshot();
    }

    public static void sendListTo(ServerPlayer player) {
        MisakaNetworkServer.send(player, buildListPacket());
        var rooms = ROOMS.values();
        if (rooms.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.academy.music_room.list_empty"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.academy.music_room.list_header"));
        for (var room : rooms) {
            String hostName;
            int memberCount;
            hostName = hostNameOf(room);
            memberCount = room.members.size();
            player.sendSystemMessage(Component.literal("  [" + room.code + "] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(room.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + memberCount + ") ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(hostName).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static void pushPendingNotices(UUID receiverUuid) {
        if (server == null) return;
        var receiver = server.getPlayerList().getPlayer(receiverUuid);
        if (receiver == null) return;
        var items = new ArrayList<MusicRoomPackets.PendingNoticePacket.PendingItem>();
        for (var entry : PENDING_ACTIONS.entrySet()) {
            var pending = entry.getValue();
            if (!pending.receiverUuid().equals(receiverUuid)) continue;
            var room = ROOMS.get(pending.roomCode());
            if (room == null) continue;
            items.add(new MusicRoomPackets.PendingNoticePacket.PendingItem(
                    entry.getKey(),
                    pending.kind() == PendingAction.Kind.APPLY,
                    room.name,
                    pending.subjectName()
            ));
        }
        MisakaNetworkServer.send(receiver, new MusicRoomPackets.PendingNoticePacket(items));
    }

    private static void addMember(Room room, ServerPlayer player) {
        if (roomOf(player) != null) {
            sendError(player, "message.academy.music_room.already_in_room");
            return;
        }
        boolean joined;
        joined = room.members.size() < maxMembers;
        if (joined) {
            room.members.put(player.getUUID(), player.getGameProfile().name());
        }
        if (!joined) {
            sendError(player, "message.academy.music_room.room_full");
            return;
        }
        PLAYER_ROOMS.put(player.getUUID(), room.code);
        player.sendSystemMessage(Component.translatable(
                "message.academy.music_room.joined", room.name
        ).withStyle(ChatFormatting.GOLD));
        announce(room, Component.translatable(
                "message.academy.music_room.member_joined", player.getGameProfile().name()
        ));
        broadcastRoom(room);
        broadcastRoomList();
    }

    private static @Nullable Room requireRoom(ServerPlayer player, String operation) {
        var room = roomOf(player);
        if (room == null) {
            sendError(player, "message.academy.music_room.not_in_room");
            return null;
        }
        return room;
    }

    private static @Nullable Room requireHostRoom(ServerPlayer player, String operation) {
        var room = requireRoom(player, operation);
        if (room == null) return null;
        if (!room.host.equals(player.getUUID())) {
            sendError(player, "message.academy.music_room.not_host");
            return null;
        }
        return room;
    }

    private static @Nullable Room roomOf(ServerPlayer player) {
        var code = PLAYER_ROOMS.get(player.getUUID());
        return code == null ? null : ROOMS.get(code);
    }

    private static Room newRoom(ServerPlayer host, String name) {
        String code;
        do {
            code = randomCode();
        } while (ROOMS.containsKey(code));
        return new Room(code, name, host.getUUID(), host.getGameProfile().name());
    }

    private static String randomCode() {
        var builder = new StringBuilder(4);
        for (var index = 0; index < 4; index++) {
            builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String normalizeCode(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    static String sanitizeName(String raw) {
        var cleaned = new StringBuilder();
        for (var ch : raw.toCharArray()) {
            if (ch == '§' || Character.isISOControl(ch)) continue;
            cleaned.append(ch);
        }
        var result = cleaned.toString().trim();
        return result.length() > ROOM_NAME_MAX_LENGTH ? result.substring(0, ROOM_NAME_MAX_LENGTH) : result;
    }

    public static @Nullable SharedTrackEntry sanitizeEntry(@Nullable SharedTrackEntry entry) {
        if (entry == null) return null;
        if (entry.trackId().isBlank()) return null;
        if ("local".equals(entry.provider())) {
            return new SharedTrackEntry(
                    "local",
                    clamp(entry.trackId(), 256),
                    clamp(entry.title(), 128),
                    clamp(entry.artist(), 128),
                    entry.durationSeconds(),
                    false,
                    ""
            );
        }
        if (MusicProviders.byName(entry.provider()).isEmpty()) return null;
        return new SharedTrackEntry(
                entry.provider(),
                entry.trackId(),
                clamp(entry.title(), 128),
                clamp(entry.artist(), 128),
                entry.durationSeconds(),
                entry.vip(),
                clamp(entry.artworkUrl(), 512)
        );
    }

    private static String clamp(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static String newToken(PendingAction action) {
        String token;
        do {
            token = Integer.toString(RANDOM.nextInt(0x1000000), 36);
        } while (PENDING_ACTIONS.containsKey(token));
        PENDING_ACTIONS.put(token, action);
        return token;
    }

    private static boolean isExpired(PendingAction action) {
        return System.currentTimeMillis() - action.createdAt() > TOKEN_TTL_MILLIS;
    }

    private static long cleanupTokens() {
        var now = gameTime();
        var expiredReceivers = new ArrayList<UUID>();
        var iterator = PENDING_ACTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (isExpired(entry.getValue())) {
                expiredReceivers.add(entry.getValue().receiverUuid());
                iterator.remove();
            }
        }
        for (var receiver : expiredReceivers) {
            pushPendingNotices(receiver);
        }
        return now;
    }

    private static long gameTime() {
        if (server == null) return 0L;
        return server.overworld().getGameTime();
    }

    private static void persist(boolean finalSave) {
        if (finalSave) shuttingDown = true;
        if (!roomEnabled) return;
        var snapshots = new ArrayList<MusicRoomStore.Snapshot>();
        for (var room : ROOMS.values()) {
            snapshots.add(snapshotOf(room));
        }
        MusicRoomStore.save(snapshots);
        if (finalSave) {
            AcademyCraft.LOGGER.info("Saved {} music room(s) before shutdown", snapshots.size());
        }
    }

    private static MusicRoomStore.Snapshot snapshotOf(Room room) {
        var members = new ArrayList<MusicRoomStore.Member>();
        for (var entry : room.members.entrySet()) {
            members.add(new MusicRoomStore.Member(entry.getKey().toString(), entry.getValue()));
        }
        var queue = new ArrayList<MusicRoomStore.QueueEntry>();
        for (var queueEntry : room.channel.queueSnapshot().entries()) {
            queue.add(new MusicRoomStore.QueueEntry(queueEntry.entry(), queueEntry.requesterName()));
        }
        var timeline = room.channel.timeline().orElse(null);
        var position = timeline == null ? 0f : timeline.expectedPositionSeconds(gameTime());
        return new MusicRoomStore.Snapshot(
                room.code,
                room.name,
                room.host.toString(),
                room.members.getOrDefault(room.host, "?"),
                members,
                queue,
                timeline == null ? null : timeline.entry(),
                room.channel.currentQueueEntry().map(QueueSnapshot.QueueEntry::requesterName).orElse(""),
                position
        );
    }

    private static void restoreRooms() {
        if (!roomEnabled) return;
        var snapshots = MusicRoomStore.load();
        if (snapshots.isEmpty()) return;
        int restored = 0;
        int skipped = 0;
        for (var snapshot : snapshots) {
            if (restored >= maxRooms) {
                skipped++;
                continue;
            }
            var room = buildRestoredRoom(snapshot);
            if (room == null || conflictsWithRestored(room)) {
                skipped++;
                continue;
            }
            if (ROOMS.putIfAbsent(room.code, room) != null) {
                skipped++;
                continue;
            }
            for (var uuid : room.members.keySet()) {
                PLAYER_ROOMS.put(uuid, room.code);
            }
            restored++;
        }
        if (restored > 0 || skipped > 0) {
            AcademyCraft.LOGGER.info("Restored {} music room(s), skipped {}", restored, skipped);
        }
    }

    private static @Nullable Room buildRestoredRoom(MusicRoomStore.Snapshot snapshot) {
        var code = normalizeCode(snapshot.code());
        var name = sanitizeName(snapshot.name());
        if (code.isBlank() || name.isEmpty()) return null;
        var host = parseUuid(snapshot.hostId());
        if (host == null) return null;

        var members = new LinkedHashMap<UUID, String>();
        if (snapshot.members() != null) {
            for (var member : snapshot.members()) {
                var uuid = parseUuid(member.id());
                if (uuid == null) continue;
                var memberName = member.name() == null || member.name().isBlank() ? "?" : member.name();
                members.put(uuid, memberName);
                if (members.size() >= maxMembers) break;
            }
        }
        if (!members.containsKey(host)) return null;

        var room = new Room(code, name, host, members.get(host));
        room.members.clear();
        room.members.putAll(members);

        var queue = new ArrayList<QueueSnapshot.QueueEntry>();
        if (snapshot.queue() != null) {
            for (var stored : snapshot.queue()) {
                var entry = sanitizeEntry(stored.entry());
                if (entry == null) continue;
                queue.add(new QueueSnapshot.QueueEntry(entry, stored.requesterName()));
                if (queue.size() >= MAX_QUEUE_PER_ROOM) break;
            }
        }

        PlaybackTimeline restoredTimeline = null;
        var current = sanitizeEntry(snapshot.currentEntry());
        if (current != null) {
            var position = Float.isFinite(snapshot.currentPositionSeconds())
                    ? Math.max(0.0f, snapshot.currentPositionSeconds())
                    : 0.0f;
            var duration = current.durationSeconds();
            if (duration > 0) position = Math.min(position, duration);
            restoredTimeline = PlaybackTimeline.pausedAt(current, gameTime(), position);
        }
        room.channel.restore(restoredTimeline, snapshot.currentRequester(), queue);
        return room;
    }

    private static boolean conflictsWithRestored(Room candidate) {
        for (var uuid : candidate.members.keySet()) {
            if (PLAYER_ROOMS.containsKey(uuid)) return true;
        }
        return false;
    }

    private static @Nullable UUID parseUuid(String raw) {
        if (raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String nameOf(@Nullable MinecraftServer server, UUID uuid) {
        if (server == null) return "?";
        var player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player.getGameProfile().name();
        return "?";
    }

    private static String hostNameOf(Room room) {
        var name = room.members.get(room.host);
        return name != null ? name : nameOf(server, room.host);
    }

    private static void announce(Room room, Component message) {
        if (server == null) return;
        for (var uuid : room.memberUuids()) {
            var member = server.getPlayerList().getPlayer(uuid);
            if (member != null) {
                member.sendSystemMessage(message);
            }
        }
    }

    private static void broadcastRoom(Room room) {
        if (server == null) return;
        MusicRoomPackets.SyncPacket packet;
        List<UUID> members;
        String hostName;
        UUID host;
        members = room.memberUuids();
        host = room.host;
        hostName = hostNameOf(room);
        var stored = room.channel.timeline().orElse(null);
        packet = new MusicRoomPackets.SyncPacket(
                true,
                room.code,
                room.name,
                hostName,
                false,
                room.memberNames(),
                stored == null ? null : stored.reanchored(gameTime()),
                room.channel.queueSnapshot(),
                gameTime()
        );
        for (var uuid : members) {
            var member = server.getPlayerList().getPlayer(uuid);
            if (member == null) continue;
            var personalized = uuid.equals(host)
                    ? new MusicRoomPackets.SyncPacket(
                    true, room.code, room.name, hostName, true,
                    packet.members(), packet.timeline(), packet.queue(), packet.serverGameTime()
            )
                    : packet;
            MisakaNetworkServer.send(member, personalized);
        }
    }

    private static void broadcastRoomList() {
        if (server == null) return;
        var packet = buildListPacket();
        for (var player : server.getPlayerList().getPlayers()) {
            MisakaNetworkServer.send(player, packet);
        }
    }

    private static MusicRoomPackets.ListPacket buildListPacket() {
        var summaries = new ArrayList<MusicRoomPackets.RoomSummary>();
        for (var room : ROOMS.values()) {
            summaries.add(new MusicRoomPackets.RoomSummary(
                    room.code, room.name, hostNameOf(room), room.members.size()
            ));
        }
        return new MusicRoomPackets.ListPacket(summaries);
    }

    private static void sendError(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    private static Component label(String key) {
        return Component.translatable(key);
    }

    static final class Room {
        final String code;
        volatile String name;
        volatile UUID host;
        final Map<UUID, String> members = new LinkedHashMap<>();
        final SharedPlaybackChannel channel = new SharedPlaybackChannel();

        Room(String code, String name, UUID host, String hostName) {
            this.code = code;
            this.name = name;
            this.host = host;
            this.members.put(host, hostName);
        }

        List<UUID> memberUuids() {
            return new ArrayList<>(members.keySet());
        }

        List<String> memberNames() {
            return new ArrayList<>(members.values());
        }
    }

    record PendingAction(Kind kind, String roomCode, UUID subjectUuid, String subjectName, UUID receiverUuid,
                         long createdAt) {
        enum Kind {
            APPLY,
            INVITE
        }
    }
}
