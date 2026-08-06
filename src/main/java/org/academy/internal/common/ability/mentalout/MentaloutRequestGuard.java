package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Connection-scoped replay protection for Mentalout client requests. */
public final class MentaloutRequestGuard {
    static final long ROSTER_RESYNC_COOLDOWN_TICKS = 20L;

    private static final SequenceCounter CLIENT_SEQUENCE = new SequenceCounter();
    private static final Map<UUID, PlayerSession> SERVER_SESSIONS = new HashMap<>();

    private MentaloutRequestGuard() {
    }

    public static synchronized long nextClientSequence() {
        return CLIENT_SEQUENCE.next();
    }

    public static synchronized boolean acceptSkillUse(
            ServerGamePacketListenerImpl listener,
            SkillUse skill,
            long requestSequence
    ) {
        Objects.requireNonNull(skill, "skill");
        return session(listener).skillSequences.accept(skill, requestSequence);
    }

    public static synchronized boolean acceptRosterResync(
            ServerGamePacketListenerImpl listener,
            long serverTick
    ) {
        return session(listener).rosterResyncs.accept(serverTick);
    }

    public static synchronized void release(UUID playerUuid) {
        SERVER_SESSIONS.remove(playerUuid);
    }

    public static synchronized void clear() {
        SERVER_SESSIONS.clear();
    }

    private static PlayerSession session(ServerGamePacketListenerImpl listener) {
        Objects.requireNonNull(listener, "listener");
        var playerUuid = listener.getPlayer().getUUID();
        var current = SERVER_SESSIONS.get(playerUuid);
        if (current == null || current.listener != listener) {
            current = new PlayerSession(listener);
            SERVER_SESSIONS.put(playerUuid, current);
        }
        return current;
    }

    public enum SkillUse {
        MENTAL_INTERVENTION,
        TARGET_MISIDENTIFICATION,
        MENTAL_STUPOR,
        IMPRESSION_MANIPULATION,
        MENTAL_INTRUSION,
        SENSORY_DISTORTION,
        PRECISION_OPERATION
    }

    static final class SequenceCounter {
        private long next;

        SequenceCounter() {
            this(0L);
        }

        SequenceCounter(long next) {
            if (next < 0L) throw new IllegalArgumentException("Sequence must be non-negative");
            this.next = next;
        }

        long next() {
            var result = next;
            next = result == Long.MAX_VALUE ? 0L : result + 1L;
            return result;
        }
    }

    static final class SkillSequenceState {
        private static final long HALF_RANGE = 1L << 62;
        private final EnumMap<SkillUse, Long> latest = new EnumMap<>(SkillUse.class);

        boolean accept(SkillUse skill, long requestSequence) {
            if (requestSequence < 0L) return false;
            var previous = latest.get(skill);
            if (previous == null || isNewer(requestSequence, previous)) {
                latest.put(skill, requestSequence);
                return true;
            }
            return false;
        }

        private static boolean isNewer(long candidate, long previous) {
            var forwardDistance = (candidate - previous) & Long.MAX_VALUE;
            return forwardDistance != 0L && forwardDistance < HALF_RANGE;
        }
    }

    static final class TickCooldownGate {
        private final long cooldownTicks;
        private boolean initialized;
        private long lastAcceptedTick;

        TickCooldownGate(long cooldownTicks) {
            if (cooldownTicks < 1L) throw new IllegalArgumentException("Cooldown must be positive");
            this.cooldownTicks = cooldownTicks;
        }

        boolean accept(long serverTick) {
            if (initialized && serverTick >= lastAcceptedTick
                    && serverTick - lastAcceptedTick < cooldownTicks) return false;
            initialized = true;
            lastAcceptedTick = serverTick;
            return true;
        }
    }

    private static final class PlayerSession {
        private final ServerGamePacketListenerImpl listener;
        private final SkillSequenceState skillSequences = new SkillSequenceState();
        private final TickCooldownGate rosterResyncs = new TickCooldownGate(ROSTER_RESYNC_COOLDOWN_TICKS);

        private PlayerSession(ServerGamePacketListenerImpl listener) {
            this.listener = listener;
        }
    }
}
