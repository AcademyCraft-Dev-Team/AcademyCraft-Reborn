package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;
import org.academy.internal.common.ability.mentalout.skills.MentaloutTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MentalIntrusionManager {
    private static final int READY_TIMEOUT_TICKS = 20;
    private static final int PERCEPTION_PRIORITY = 105;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<CooldownKey, Long> PLAYER_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> SESSION_REVISIONS = new HashMap<>();
    private static final Map<UUID, Long> FILTER_REVISIONS = new HashMap<>();
    private static boolean clientInitialized;
    private static boolean serverInitialized;

    private MentalIntrusionManager() {
    }

    public static synchronized void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static StartResult toggle(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player.getUUID(), true);
            return StartResult.STOPPED;
        }
        var skill = Skills.MENTAL_INTRUSION.get();
        if (!skill.isEnabled(player)) return StartResult.UNAVAILABLE;
        var level = Math.clamp(skill.getLevel(player), 0, 2);
        var target = MentaloutTargeting.findLookedAtLiving(
                player,
                MentaloutConfig.mentalIntrusionRange(player, level)
        );
        if (target == null) return StartResult.INVALID_TARGET;
        if (MentalControlRuntime.isProtectedTarget(target)) {
            MentalControlRuntime.notifyProtectionBlocked(player, target);
            return StartResult.PROTECTED_NOTIFIED;
        }
        if (target instanceof ServerPlayer targetPlayer && targetPlayer.isSpectator()
                || target instanceof ServerPlayer && FriendlyFireSetting.shouldPrevent(player, target)) {
            return StartResult.PROTECTED;
        }
        var now = player.level().getGameTime();
        var cooldownKey = new CooldownKey(player.getUUID(), target.getUUID());
        if (target instanceof ServerPlayer
                && PLAYER_COOLDOWNS.getOrDefault(cooldownKey, Long.MIN_VALUE) > now) {
            return StartResult.COOLDOWN;
        }
        if (!AbilitySystemServer.getSystem(player).replacePermanentOccupation(
                player.getUUID(),
                MentaloutConfig.mentalIntrusionCost(player, level),
                skill
        )) {
            return StartResult.INSUFFICIENT_CP;
        }

        var revision = nextRevision(SESSION_REVISIONS, player.getUUID());
        var sessionId = UUID.randomUUID();
        var maximumEnd = Long.MAX_VALUE;
        var session = new Session(
                sessionId,
                revision,
                player,
                target,
                now + READY_TIMEOUT_TICKS,
                maximumEnd,
                true
        );
        SESSIONS.put(player.getUUID(), session);
        if (target instanceof ServerPlayer) {
            PLAYER_COOLDOWNS.put(cooldownKey, now + MentaloutConfig.playerIntrusionCooldown(player));
        }
        MisakaNetworkServer.send(player, new BeginPacket(
                sessionId,
                revision,
                target.getId(),
                target.getUUID()
        ));
        player.level().playSound(null, player.blockPosition(),
                org.academy.internal.common.sounds.SoundEvents.MENTAL_INTRUSION.get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
        return StartResult.STARTED;
    }

    public static UUID startPrecision(ServerPlayer player, LivingEntity target, long expiresAt) {
        if (player == null || target == null || SESSIONS.containsKey(player.getUUID())) return null;
        var intrusion = Skills.MENTAL_INTRUSION.get();
        if (!intrusion.isEnabled(player)) return null;
        var level = Math.clamp(intrusion.getLevel(player), 0, 2);
        var range = MentaloutConfig.mentalIntrusionRange(player, level);
        if (MentalControlRuntime.isProtectedTarget(target)) {
            MentalControlRuntime.notifyProtectionBlocked(player, target);
            return null;
        }
        if (!MentaloutTargeting.isValidTarget(player, target, range)
                || target instanceof ServerPlayer targetPlayer && targetPlayer.isSpectator()
                || target instanceof ServerPlayer && FriendlyFireSetting.shouldPrevent(player, target)) {
            return null;
        }
        var now = player.level().getGameTime();
        // Player intrusion currently has no duration cap, including precision-operation
        // sessions. The owning operation can still stop the session explicitly.
        var maximumEnd = target instanceof ServerPlayer
                ? Long.MAX_VALUE
                : Math.max(now + 1L, expiresAt);
        if (target instanceof ServerPlayer) {
            var cooldownKey = new CooldownKey(player.getUUID(), target.getUUID());
            if (PLAYER_COOLDOWNS.getOrDefault(cooldownKey, Long.MIN_VALUE) > now) return null;
            PLAYER_COOLDOWNS.put(cooldownKey, now + MentaloutConfig.playerIntrusionCooldown(player));
        }
        var revision = nextRevision(SESSION_REVISIONS, player.getUUID());
        var sessionId = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(
                sessionId,
                revision,
                player,
                target,
                now + READY_TIMEOUT_TICKS,
                maximumEnd,
                false
        ));
        MisakaNetworkServer.send(player, new BeginPacket(
                sessionId,
                revision,
                target.getId(),
                target.getUUID()
        ));
        return sessionId;
    }

    public static void stopPrecision(ServerPlayer player, UUID sessionId) {
        if (player == null || sessionId == null) return;
        var session = SESSIONS.get(player.getUUID());
        if (session != null && !session.ownsOccupation && session.id.equals(sessionId)) {
            stop(player.getUUID(), true);
        }
    }

    public static boolean isPrecisionActive(ServerPlayer player, UUID sessionId) {
        if (player == null || sessionId == null) return false;
        var session = SESSIONS.get(player.getUUID());
        return session != null && !session.ownsOccupation && session.id.equals(sessionId);
    }

    public static void stopAny(ServerPlayer player) {
        if (player != null) stop(player.getUUID(), true);
    }

    public static DistortionResult toggleDistortion(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        var skill = Skills.SENSORY_DISTORTION.get();
        if (session == null || !session.confirmed) return DistortionResult.NO_SESSION;
        if (!skill.isEnabled(player)) return DistortionResult.UNAVAILABLE;
        if (session.distortion != null && !session.distortion.isClosed()) {
            session.distortion.close();
            session.distortion = null;
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    skill.getKeyString()
            );
            return DistortionResult.STOPPED;
        }
        if (MentalControlRuntime.isProtectedTarget(session.target)) {
            MentalControlRuntime.notifyProtectionBlocked(player, session.target);
            return DistortionResult.PROTECTED_NOTIFIED;
        }
        var level = Math.clamp(skill.getLevel(player), 0, 2);
        var cost = MentaloutConfig.sensoryDistortionCost(player, level);
        if (MentalControlRuntime.isBossCost(session.target)) {
            cost *= MentaloutConfig.bossCostMultiplier(player);
        }
        if (!AbilitySystemServer.getSystem(player).replacePermanentOccupation(
                player.getUUID(),
                cost,
                skill
        )) {
            return DistortionResult.INSUFFICIENT_CP;
        }
        try {
            session.distortion = MentalPerceptionRuntime.apply(
                    player,
                    session.target,
                    player,
                    skill.getKey(),
                    PERCEPTION_PRIORITY,
                    session.maximumEnd
            );
            player.level().playSound(null, player.blockPosition(),
                    org.academy.internal.common.sounds.SoundEvents.SENSORY_DISTORTION.get(),
                    SoundSource.PLAYERS, 0.65f, 1.0f);
            return DistortionResult.STARTED;
        } catch (RuntimeException exception) {
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(),
                    skill.getKeyString()
            );
            if (MentalControlRuntime.isProtectedTarget(session.target)) {
                MentalControlRuntime.notifyProtectionBlocked(player, session.target);
                return DistortionResult.PROTECTED_NOTIFIED;
            }
            return DistortionResult.PROTECTED;
        }
    }

    public static LivingEntity target(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.target;
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        MentalPerceptionRuntime.tick(server);
        var now = server.overworld().getGameTime();
        for (var session : List.copyOf(SESSIONS.values())) {
            var player = session.player;
            var target = session.target;
            var maxDistance = MentaloutConfig.intrusionMaximumDistance(player);
            var protectedTarget = MentalControlRuntime.isProtectedTarget(target);
            if (!player.isAlive()
                    || !Skills.MENTAL_INTRUSION.get().isEnabled(player)
                    || target.isRemoved()
                    || !target.isAlive()
                    || target.level() != player.level()
                    || target.distanceToSqr(player) > maxDistance * maxDistance
                    || protectedTarget
                    || !session.confirmed && now >= session.readyDeadline
                    || now >= session.maximumEnd) {
                if (protectedTarget) MentalControlRuntime.notifyProtectionBlocked(player, target);
                stop(player.getUUID(), true);
            } else {
                Skills.MENTAL_INTRUSION.get().reportActivity(player, session.confirmed);
                if (session.distortion != null && !session.distortion.isClosed()) {
                    Skills.SENSORY_DISTORTION.get().reportActivity(player, true);
                }
            }
        }
        PLAYER_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public static void releaseEntity(UUID entityId) {
        MentalPerceptionRuntime.releaseEntity(entityId);
        for (var session : List.copyOf(SESSIONS.values())) {
            if (session.player.getUUID().equals(entityId) || session.target.getUUID().equals(entityId)) {
                stop(session.player.getUUID(), true);
            }
        }
    }

    public static void releaseController(UUID controllerId) {
        stop(controllerId, true);
        MentalPerceptionRuntime.releaseController(controllerId);
    }

    public static void clear() {
        List.copyOf(SESSIONS.keySet()).forEach(id -> stop(id, true));
        SESSIONS.clear();
        PLAYER_COOLDOWNS.clear();
        SESSION_REVISIONS.clear();
        FILTER_REVISIONS.clear();
        MentalPerceptionRuntime.clear();
    }

    public static void sendPerception(ServerPlayer observer, LivingEntity hidden, boolean active) {
        var revision = nextRevision(FILTER_REVISIONS, observer.getUUID());
        MisakaNetworkServer.send(observer, new PerceptionPacket(
                hidden.getUUID(),
                hidden.getId(),
                active,
                revision
        ));
    }

    private static void ready(ServerPlayer player, UUID sessionId, long revision, boolean ready) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(sessionId) || session.revision != revision) return;
        if (!ready) {
            stop(player.getUUID(), true);
            return;
        }
        session.confirmed = true;
    }

    private static void stopFromClient(ServerPlayer player, UUID sessionId, long revision) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(sessionId) || session.revision != revision) return;
        stop(player.getUUID(), true);
    }

    private static void stop(UUID controllerId, boolean notifyClient) {
        var session = SESSIONS.remove(controllerId);
        if (session == null) return;
        if (session.distortion != null) session.distortion.close();
        var system = AbilitySystemServer.getSystem(session.player);
        if (session.ownsOccupation) {
            system.releaseMaintenanceOccupation(controllerId, Skills.MENTAL_INTRUSION.get().getKeyString());
        }
        system.releaseMaintenanceOccupation(controllerId, Skills.SENSORY_DISTORTION.get().getKeyString());
        if (notifyClient) {
            var revision = nextRevision(SESSION_REVISIONS, controllerId);
            MisakaNetworkServer.send(session.player, new EndPacket(session.id, revision));
        }
    }

    private static long nextRevision(Map<UUID, Long> revisions, UUID key) {
        var next = revisions.getOrDefault(key, 0L) + 1L;
        revisions.put(key, next);
        return next;
    }

    private static void feedback(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
    }

    public enum StartResult {
        STARTED,
        STOPPED,
        INVALID_TARGET,
        PROTECTED,
        PROTECTED_NOTIFIED,
        COOLDOWN,
        INSUFFICIENT_CP,
        UNAVAILABLE
    }

    public enum DistortionResult {
        STARTED,
        STOPPED,
        NO_SESSION,
        PROTECTED,
        PROTECTED_NOTIFIED,
        INSUFFICIENT_CP,
        UNAVAILABLE
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void toggle(TogglePacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(),
                    MentaloutRequestGuard.SkillUse.MENTAL_INTRUSION,
                    packet.sequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            switch (MentalIntrusionManager.toggle(player)) {
                case INVALID_TARGET -> feedback(player, "message.academy.mentalout.invalid_target");
                case PROTECTED -> feedback(player, "message.academy.mentalout.protected_target");
                case PROTECTED_NOTIFIED -> {
                }
                case COOLDOWN -> feedback(player, "message.academy.mentalout.intrusion_cooldown");
                case INSUFFICIENT_CP -> feedback(player, "message.academy.mentalout.insufficient_cp");
                case UNAVAILABLE -> feedback(player, "message.academy.mentalout.skill_unavailable");
                default -> {
                }
            }
        }

        @SubscribePacket
        public static void distortion(DistortionPacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(),
                    MentaloutRequestGuard.SkillUse.SENSORY_DISTORTION,
                    packet.sequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            switch (MentalIntrusionManager.toggleDistortion(player)) {
                case NO_SESSION -> feedback(player, "message.academy.mentalout.no_intrusion_session");
                case PROTECTED -> feedback(player, "message.academy.mentalout.protected_target");
                case PROTECTED_NOTIFIED -> {
                }
                case INSUFFICIENT_CP -> feedback(player, "message.academy.mentalout.insufficient_cp");
                case UNAVAILABLE -> feedback(player, "message.academy.mentalout.skill_unavailable");
                default -> {
                }
            }
        }

        @SubscribePacket
        public static void ready(ReadyPacket packet) {
            MentalIntrusionManager.ready(
                    packet.getPacketListener().getPlayer(),
                    packet.sessionId,
                    packet.revision,
                    packet.ready
            );
        }

        @SubscribePacket
        public static void clientStop(ClientStopPacket packet) {
            MentalIntrusionManager.stopFromClient(
                    packet.getPacketListener().getPlayer(),
                    packet.sessionId,
                    packet.revision
            );
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void begin(BeginPacket packet) {
            MentalIntrusionClientState.begin(
                    packet.sessionId,
                    packet.revision,
                    packet.targetEntityId,
                    packet.targetUuid
            );
        }

        @SubscribePacket
        public static void end(EndPacket packet) {
            MentalIntrusionClientState.end(packet.sessionId, packet.revision);
        }

        @SubscribePacket
        public static void perception(PerceptionPacket packet) {
            MentalIntrusionClientState.applyFilter(
                    packet.hiddenUuid,
                    packet.hiddenEntityId,
                    packet.active,
                    packet.revision
            );
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = ByteBufCodecs.LONG.map(
                TogglePacket::new,
                packet -> packet.sequence
        );
        private final long sequence;

        public TogglePacket(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.MENTAL_INTRUSION_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DistortionPacket extends Packet<ServerGamePacketListenerImpl, DistortionPacket> {
        public static final StreamCodec<ByteBuf, DistortionPacket> CODEC = ByteBufCodecs.LONG.map(
                DistortionPacket::new,
                packet -> packet.sequence
        );
        private final long sequence;

        public DistortionPacket(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DistortionPacket> getPacketType() {
            return PacketTypes.SENSORY_DISTORTION_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReadyPacket extends Packet<ServerGamePacketListenerImpl, ReadyPacket> {
        public static final StreamCodec<ByteBuf, ReadyPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeBoolean(packet.ready);
                },
                buf -> new ReadyPacket(readUuid(buf), buf.readLong(), buf.readBoolean())
        );
        private final UUID sessionId;
        private final long revision;
        private final boolean ready;

        public ReadyPacket(UUID sessionId, long revision, boolean ready) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.ready = ready;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReadyPacket> getPacketType() {
            return PacketTypes.MENTAL_INTRUSION_READY.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ClientStopPacket extends Packet<ServerGamePacketListenerImpl, ClientStopPacket> {
        public static final StreamCodec<ByteBuf, ClientStopPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                },
                buf -> new ClientStopPacket(readUuid(buf), buf.readLong())
        );
        private final UUID sessionId;
        private final long revision;

        public ClientStopPacket(UUID sessionId, long revision) {
            this.sessionId = sessionId;
            this.revision = revision;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ClientStopPacket> getPacketType() {
            return PacketTypes.MENTAL_INTRUSION_CLIENT_STOP.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class BeginPacket extends Packet<ClientPacketListener, BeginPacket> {
        public static final StreamCodec<ByteBuf, BeginPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetEntityId);
                    writeUuid(buf, packet.targetUuid);
                },
                buf -> new BeginPacket(
                        readUuid(buf),
                        buf.readLong(),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        readUuid(buf)
                )
        );
        private final UUID sessionId;
        private final long revision;
        private final int targetEntityId;
        private final UUID targetUuid;

        public BeginPacket(UUID sessionId, long revision, int targetEntityId, UUID targetUuid) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.targetEntityId = targetEntityId;
            this.targetUuid = targetUuid;
        }

        @Override
        public PacketType<ClientPacketListener, BeginPacket> getPacketType() {
            return PacketTypes.MENTAL_INTRUSION_BEGIN.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class EndPacket extends Packet<ClientPacketListener, EndPacket> {
        public static final StreamCodec<ByteBuf, EndPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                },
                buf -> new EndPacket(readUuid(buf), buf.readLong())
        );
        private final UUID sessionId;
        private final long revision;

        public EndPacket(UUID sessionId, long revision) {
            this.sessionId = sessionId;
            this.revision = revision;
        }

        @Override
        public PacketType<ClientPacketListener, EndPacket> getPacketType() {
            return PacketTypes.MENTAL_INTRUSION_END.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class PerceptionPacket extends Packet<ClientPacketListener, PerceptionPacket> {
        public static final StreamCodec<ByteBuf, PerceptionPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.hiddenUuid);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.hiddenEntityId);
                    buf.writeBoolean(packet.active);
                    buf.writeLong(packet.revision);
                },
                buf -> new PerceptionPacket(
                        readUuid(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readBoolean(),
                        buf.readLong()
                )
        );
        private final UUID hiddenUuid;
        private final int hiddenEntityId;
        private final boolean active;
        private final long revision;

        public PerceptionPacket(UUID hiddenUuid, int hiddenEntityId, boolean active, long revision) {
            this.hiddenUuid = hiddenUuid;
            this.hiddenEntityId = hiddenEntityId;
            this.active = active;
            this.revision = revision;
        }

        @Override
        public PacketType<ClientPacketListener, PerceptionPacket> getPacketType() {
            return PacketTypes.MENTAL_PERCEPTION_UPDATE.get();
        }
    }

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private record CooldownKey(UUID controllerId, UUID targetId) {
    }

    private static final class Session {
        private final UUID id;
        private final long revision;
        private final ServerPlayer player;
        private final LivingEntity target;
        private final long readyDeadline;
        private final long maximumEnd;
        private final boolean ownsOccupation;
        private boolean confirmed;
        private MentalPerceptionRuntime.Handle distortion;

        private Session(
                UUID id,
                long revision,
                ServerPlayer player,
                LivingEntity target,
                long readyDeadline,
                long maximumEnd,
                boolean ownsOccupation
        ) {
            this.id = id;
            this.revision = revision;
            this.player = player;
            this.target = target;
            this.readyDeadline = readyDeadline;
            this.maximumEnd = maximumEnd;
            this.ownsOccupation = ownsOccupation;
        }
    }
}
