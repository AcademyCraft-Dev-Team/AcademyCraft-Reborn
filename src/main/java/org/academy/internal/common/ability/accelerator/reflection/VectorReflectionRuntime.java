package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.coremod.ClassPointerProtectionManager;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side class-pointer integrity, removal recovery, and observer reconstruction.
 */
public final class VectorReflectionRuntime {
    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();
    private static final long OBSERVER_REBUILD_COOLDOWN = 20L;

    private VectorReflectionRuntime() {
    }

    public static void maintain(ServerPlayer player) {
        if (player == null) return;
        var anchor = ANCHORS.computeIfAbsent(player.getUUID(), ignored -> new Anchor(player));
        var previous = anchor.player.get();
        if (previous != null && previous != player) {
            EntityControlApi.allowExternalRemoval(previous);
            ClassPointerProtectionManager.restore(previous);
        }
        ClassPointerProtectionManager.ensureServerPlayer(player);
        anchor.player = new WeakReference<>(player);

        EntityControlApi.protectFromExternalRemoval(player);
        sanitize(player, anchor);
        recoverLevelRegistration(player, anchor);
        rebuildObserversIfRequested(player, anchor);
    }

    public static void deactivate(ServerPlayer player) {
        if (player == null) return;
        VectorReflection.Server.restoreRecordedHealth(player);
        restoreOriginalInstance(player);
    }

    public static void deactivateForDeath(ServerPlayer player) {
        if (player == null) return;
        // Death has already won the race. Restoring an older protected snapshot here would leave a
        // dead entity with positive health, so only discard the snapshot and repair the class.
        VectorReflection.Server.discardRecordedHealth(player);
        restoreOriginalInstance(player);
    }

    private static void restoreOriginalInstance(ServerPlayer player) {
        var anchor = ANCHORS.remove(player.getUUID());
        var previous = anchor == null ? null : anchor.player.get();
        if (previous != null && previous != player) {
            EntityControlApi.allowExternalRemoval(previous);
            ClassPointerProtectionManager.restore(previous);
        }
        EntityControlApi.allowExternalRemoval(player);
        ClassPointerProtectionManager.restore(player);
    }

    public static void onServerTick() {
        for (var entry : ANCHORS.entrySet()) {
            var player = entry.getValue().player.get();
            if (player == null || player.connection == null || player.hasDisconnected()) {
                if (player != null) deactivate(player);
                else ANCHORS.remove(entry.getKey(), entry.getValue());
                continue;
            }
            if (!VectorReflection.Server.isActive(player)) {
                deactivate(player);
                continue;
            }
            maintain(player);
        }
    }

    public static void shutdown() {
        for (var anchor : ANCHORS.values()) {
            var player = anchor.player.get();
            if (player == null) continue;
            VectorReflection.Server.restoreRecordedHealth(player);
            EntityControlApi.allowExternalRemoval(player);
            ClassPointerProtectionManager.restore(player);
        }
        ANCHORS.clear();
        ClassPointerProtectionManager.restoreAllServer();
    }

    private static void sanitize(ServerPlayer player, Anchor anchor) {
        if (player.isRemoved()) {
            player.revive();
            anchor.observerRebuildRequested = true;
        }
        player.setTicksFrozen(0);
        player.setInvisible(false);
        player.clearFire();
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        var position = player.position();
        if (finite(position)) {
            anchor.lastSafePosition = position;
        } else if (anchor.lastSafePosition != null) {
            EntityMotionGuard.runInternalCorrection(
                    player,
                    () -> player.snapTo(anchor.lastSafePosition)
            );
            anchor.observerRebuildRequested = true;
        }
    }

    private static void recoverLevelRegistration(ServerPlayer player, Anchor anchor) {
        var level = player.level();
        var registered = level.getEntity(player.getUUID());
        if (registered == null) {
            try {
                player.revive();
                level.players().removeIf(candidate -> candidate == player);
                level.addDuringTeleport(player);
                anchor.observerRebuildRequested = true;
            } catch (Throwable error) {
                logRecoveryFailure(player, "level registration", error, anchor);
            }
        } else if (registered != player) {
            logRecoveryFailure(player, "duplicate UUID registration", null, anchor);
        }

        if (!level.getChunkSource().hasEntityWithId(player.getId())) {
            anchor.observerRebuildRequested = true;
        }
    }

    private static void rebuildObserversIfRequested(ServerPlayer player, Anchor anchor) {
        if (!anchor.observerRebuildRequested) return;
        var level = player.level();
        var registered = level.getEntity(player.getUUID());
        var registeredAsPlayer = registered == player;
        var conflictingRegistration = registered != null && !registeredAsPlayer;
        var chunkTracked = level.getChunkSource().hasEntityWithId(player.getId());
        if (!shouldRebuildObservers(
                player.isRemoved(), registeredAsPlayer, conflictingRegistration, chunkTracked)) {
            // A canceled kill/remove attempt must never churn an otherwise healthy tracker. This
            // final guard also makes stale repair requests harmless.
            anchor.observerRebuildRequested = false;
            return;
        }
        var now = player.level().getGameTime();
        if (now - anchor.lastObserverRebuildTick < OBSERVER_REBUILD_COOLDOWN) return;

        try {
            var chunkSource = player.level().getChunkSource();
            if (chunkSource.hasEntityWithId(player.getId())) chunkSource.removeEntity(player);
            chunkSource.addEntity(player);
            chunkSource.move(player);
            anchor.observerRebuildRequested = false;
            anchor.lastObserverRebuildTick = now;
        } catch (Throwable error) {
            logRecoveryFailure(player, "observer reconstruction", error, anchor);
        }
    }

    static boolean shouldRebuildObservers(boolean removed, boolean registeredAsPlayer,
                                          boolean conflictingRegistration, boolean chunkTracked) {
        if (conflictingRegistration) return false;
        return removed || !registeredAsPlayer || !chunkTracked;
    }

    private static void logRecoveryFailure(ServerPlayer player, String operation,
                                           Throwable error, Anchor anchor) {
        var now = System.nanoTime();
        if (now - anchor.lastFailureLogNanos < 5_000_000_000L) return;
        anchor.lastFailureLogNanos = now;
        if (error == null) {
            AcademyCraft.getLogger().warn("Vector Reflection could not repair {} for {}",
                    operation, player.getGameProfile().name());
        } else {
            AcademyCraft.getLogger().warn("Vector Reflection failed to repair {} for {}",
                    operation, player.getGameProfile().name(), error);
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static final class Anchor {
        private volatile WeakReference<ServerPlayer> player;
        private volatile Vec3 lastSafePosition;
        private volatile boolean observerRebuildRequested;
        private volatile long lastObserverRebuildTick = Long.MIN_VALUE / 2L;
        private volatile long lastFailureLogNanos;

        private Anchor(ServerPlayer player) {
            this.player = new WeakReference<>(player);
            lastSafePosition = finite(player.position()) ? player.position() : Vec3.ZERO;
        }
    }
}
