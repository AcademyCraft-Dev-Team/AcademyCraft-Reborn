package org.academy.internal.client.time;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.academy.api.server.time.TemporalAccumulator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-side wall-clock compensation for server-authorized immunity. */
public final class TemporalClientRuntime {
    private static final long TICK_NANOS = 50_000_000L;
    private static final long FREEZE_GRACE_NANOS = 55_000_000L;
    private static final Map<UUID, TickState> TICK_STATES = new HashMap<>();
    private static final Map<UUID, ScaledTickState> SCALED_TICK_STATES =
            new HashMap<>();
    private static final Set<UUID> SCALED_TICK_STACK = new HashSet<>();
    private static Map<UUID, Integer> immunityMasks = Map.of();
    private static Map<UUID, Float> playerScales = Map.of();
    private static UUID serverSessionId;
    private static long revision = Long.MIN_VALUE;
    private static long serverHeartbeat = Long.MIN_VALUE;
    private static UUID sessionPlayerId;
    private static boolean hadWorld;
    private static boolean compensatingLocal;
    private static boolean compensatingRemote;

    private TemporalClientRuntime() {
    }

    public static void applyState(
            UUID newSessionId,
            long newRevision,
            long newServerHeartbeat,
            Map<UUID, Integer> masks,
            Map<UUID, Float> scales
    ) {
        if (newSessionId == null) return;
        if (!newSessionId.equals(serverSessionId)) {
            serverSessionId = newSessionId;
            revision = Long.MIN_VALUE;
            serverHeartbeat = Long.MIN_VALUE;
            immunityMasks = Map.of();
            playerScales = Map.of();
            TICK_STATES.clear();
            SCALED_TICK_STATES.clear();
            SCALED_TICK_STACK.clear();
        }
        if (newRevision <= revision) return;

        var checkedScales = new HashMap<UUID, Float>();
        for (var entry : scales.entrySet()) {
            var scale = entry.getValue();
            if (scale == null || !Float.isFinite(scale) || scale < 0.0F || scale > 8.0F) {
                continue;
            }
            if (Float.compare(scale, 1.0F) != 0) {
                checkedScales.put(entry.getKey(), scale);
            }
        }
        var nextScales = Map.copyOf(checkedScales);
        SCALED_TICK_STATES.entrySet().removeIf(entry ->
                !nextScales.containsKey(entry.getKey())
                        || Float.compare(
                                entry.getValue().scale,
                                nextScales.get(entry.getKey())
                        ) != 0
        );
        revision = newRevision;
        serverHeartbeat = newServerHeartbeat;
        immunityMasks = Map.copyOf(masks);
        playerScales = nextScales;
        TICK_STATES.keySet().removeIf(entityId -> !immunityMasks.containsKey(entityId));
    }

    public static boolean isImmune(Entity entity) {
        return entity != null && immunityMasks.getOrDefault(entity.getUUID(), 0) != 0;
    }

    public static boolean isRemoteCompensationActive() {
        return compensatingRemote;
    }

    public static double effectivePlayerScale(UUID playerId) {
        return playerId == null ? 1.0D : playerScales.getOrDefault(playerId, 1.0F);
    }

    public static boolean isPlayerSimulationPaused(Player player) {
        return player != null && effectivePlayerScale(player.getUUID()) == 0.0D;
    }

    /** Dispatches a client player using only a server-authorized scale snapshot. */
    public static boolean dispatchPlayerTicks(ClientLevel level, Player player) {
        if (level == null || player == null || player.isRemoved()
                || compensatingRemote) {
            return false;
        }
        var playerId = player.getUUID();
        if (SCALED_TICK_STACK.contains(playerId)) return false;

        var scale = effectivePlayerScale(playerId);
        var logicalTicks = logicalTicks(playerId, scale);
        if (logicalTicks == 1) return false;
        if (logicalTicks == 0) return true;

        SCALED_TICK_STACK.add(playerId);
        try {
            for (var index = 0; index < logicalTicks && !player.isRemoved(); index++) {
                level.tickNonPassenger(player);
            }
        } finally {
            SCALED_TICK_STACK.remove(playerId);
        }
        return true;
    }

    public static void beforeVanillaTick(Minecraft minecraft) {
        observeProgress(minecraft, System.nanoTime());
    }

    public static void afterVanillaTick(Minecraft minecraft) {
        observeProgress(minecraft, System.nanoTime());
    }

    /** Called from the render-loop boundary even when {@link Minecraft#tick()} stalls. */
    public static void afterFrame(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            if (hadWorld) reset();
            return;
        }
        hadWorld = true;
        if (!player.getUUID().equals(sessionPlayerId)) {
            sessionPlayerId = player.getUUID();
            TICK_STATES.clear();
        }

        var now = System.nanoTime();
        observeProgress(minecraft, now);
        if (minecraft.isPaused()) return;

        if (isImmune(player)) compensateLocal(minecraft, player, now);
        compensateRemote(minecraft, player, now);
    }

    public static void reset() {
        immunityMasks = Map.of();
        playerScales = Map.of();
        serverSessionId = null;
        revision = Long.MIN_VALUE;
        serverHeartbeat = Long.MIN_VALUE;
        sessionPlayerId = null;
        hadWorld = false;
        TICK_STATES.clear();
        SCALED_TICK_STATES.clear();
        SCALED_TICK_STACK.clear();
        compensatingLocal = false;
        compensatingRemote = false;
    }

    private static int logicalTicks(UUID playerId, double scale) {
        if (scale == 1.0D) {
            SCALED_TICK_STATES.remove(playerId);
            return 1;
        }
        var state = SCALED_TICK_STATES.computeIfAbsent(
                playerId,
                ignored -> new ScaledTickState((float) scale)
        );
        if (Float.compare(state.scale, (float) scale) != 0) {
            state = new ScaledTickState((float) scale);
            SCALED_TICK_STATES.put(playerId, state);
        }
        return state.accumulator.advance(scale, 8);
    }

    private static void compensateLocal(
            Minecraft minecraft,
            LocalPlayer player,
            long now
    ) {
        if (player.isRemoved() || player.isSpectator() || player.isPassenger()
                || compensatingLocal) {
            return;
        }
        var state = TICK_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> TickState.start(player.tickCount, now)
        );
        if (!state.isStalled(now) || !state.canCompensate(now)) return;

        state.lastCompensationNanos = now;
        compensatingLocal = true;
        try {
            tickConnection(minecraft);
            try {
                player.tick();
            } catch (Throwable ignored) {
            }
            try {
                if (minecraft.gameMode != null) minecraft.gameMode.tick();
            } catch (Throwable ignored) {
            }
            try {
                minecraft.gameRenderer.tick();
            } catch (Throwable ignored) {
            }
        } finally {
            compensatingLocal = false;
        }
    }

    private static void compensateRemote(
            Minecraft minecraft,
            LocalPlayer localPlayer,
            long now
    ) {
        if (compensatingRemote || minecraft.level == null) return;
        var seen = new HashSet<UUID>();
        var connectionTicked = false;
        for (Player player : minecraft.level.players()) {
            if (player == localPlayer || !isImmune(player)) continue;
            seen.add(player.getUUID());
            if (player.isRemoved() || player.isPassenger()) continue;
            var state = TICK_STATES.computeIfAbsent(
                    player.getUUID(),
                    ignored -> TickState.start(player.tickCount, now)
            );
            if (!state.isStalled(now) || !state.canCompensate(now)) continue;

            state.lastCompensationNanos = now;
            if (!connectionTicked) {
                tickConnection(minecraft);
                connectionTicked = true;
            }
            try {
                compensatingRemote = true;
                minecraft.level.tickNonPassenger(player);
                state.observe(player.tickCount, now);
            } catch (Throwable ignored) {
                // Remote compensation is visual/predictive and may safely retry.
            } finally {
                compensatingRemote = false;
            }
        }
        TICK_STATES.keySet().removeIf(entityId ->
                !entityId.equals(localPlayer.getUUID())
                        && immunityMasks.containsKey(entityId)
                        && !seen.contains(entityId)
        );
    }

    private static void tickConnection(Minecraft minecraft) {
        try {
            var connection = minecraft.getConnection();
            if (connection != null) connection.tick();
        } catch (Throwable ignored) {
        }
    }

    private static void observeProgress(Minecraft minecraft, long now) {
        if (minecraft == null || minecraft.level == null) return;
        for (Player player : minecraft.level.players()) {
            if (!isImmune(player)) continue;
            TICK_STATES.computeIfAbsent(
                    player.getUUID(),
                    ignored -> TickState.start(player.tickCount, now)
            ).observe(player.tickCount, now);
        }
    }

    private static final class TickState {
        private int lastTickCount;
        private long lastProgressNanos;
        private long lastCompensationNanos = Long.MIN_VALUE;

        private TickState(int lastTickCount, long lastProgressNanos) {
            this.lastTickCount = lastTickCount;
            this.lastProgressNanos = lastProgressNanos;
        }

        private static TickState start(int tickCount, long now) {
            return new TickState(tickCount, now);
        }

        private void observe(int tickCount, long now) {
            if (tickCount == lastTickCount) return;
            lastTickCount = tickCount;
            lastProgressNanos = now;
        }

        private boolean isStalled(long now) {
            return now - lastProgressNanos >= FREEZE_GRACE_NANOS;
        }

        private boolean canCompensate(long now) {
            return lastCompensationNanos == Long.MIN_VALUE
                    || now - lastCompensationNanos >= TICK_NANOS;
        }
    }

    private static final class ScaledTickState {
        private final TemporalAccumulator accumulator = new TemporalAccumulator();
        private final float scale;

        private ScaledTickState(float scale) {
            this.scale = scale;
        }
    }
}
