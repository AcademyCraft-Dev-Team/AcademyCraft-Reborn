package org.academy.internal.common.ability.accelerator.reflection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.server.entity.SurvivalDefense;
import org.academy.api.server.entity.SurvivalDefenseLease;
import org.academy.api.server.entity.SurvivalDefenseProfile;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalImmunityLease;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.coremod.ClassPointerProtectionManager;

import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side class-pointer integrity, removal recovery, and observer reconstruction.
 */
public final class VectorReflectionRuntime {
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();
    private static final long OBSERVER_REBUILD_COOLDOWN = 20L;

    private VectorReflectionRuntime() {
    }

    public static void maintain(ServerPlayer player) {
        if (player == null) return;
        var anchor = ANCHORS.computeIfAbsent(player.getUUID(), ignored -> new Anchor(player));
        var previous = anchor.player.get();
        if (previous != null && previous != player) {
            closeSurvivalDefense(anchor);
            EntityControlApi.allowExternalRemoval(previous);
            ClassPointerProtectionManager.restore(previous);
        }
        ClassPointerProtectionManager.ensureServerPlayer(player);
        // Initialize the generated ledger before any foreign synced-data write can become its seed.
        player.getHealth();
        anchor.player = new WeakReference<>(player);

        EntityControlApi.protectFromExternalRemoval(player);
        maintainSurvivalDefense(player, anchor);
        maintainTemporalImmunity(player, anchor);
        sanitize(player, anchor);
        recoverLevelRegistration(player, anchor);
        rebuildObserversIfRequested(player, anchor);
    }

    public static void deactivate(ServerPlayer player) {
        if (player == null) return;
        if (!isAcademyStateCaller("deactivate")) return;
        restoreOriginalInstance(player);
    }

    public static void deactivateForDeath(ServerPlayer player) {
        if (player == null) return;
        if (!isAcademyStateCaller("deactivateForDeath")) return;
        restoreOriginalInstance(player);
    }

    private static void restoreOriginalInstance(ServerPlayer player) {
        var anchor = ANCHORS.remove(player.getUUID());
        closeSurvivalDefense(anchor);
        closeTemporalImmunity(anchor);
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
            if (!VectorReflection.Server.usesFullInstanceProtection(player)) {
                deactivate(player);
                continue;
            }
            maintain(player);
        }
    }

    public static void shutdown() {
        if (!isAcademyStateCaller("shutdown")) return;
        for (var anchor : ANCHORS.values()) {
            var player = anchor.player.get();
            closeSurvivalDefense(anchor);
            closeTemporalImmunity(anchor);
            if (player == null) continue;
            EntityControlApi.allowExternalRemoval(player);
            ClassPointerProtectionManager.restore(player);
        }
        ANCHORS.clear();
        ClassPointerProtectionManager.restoreAllServer();
    }

    private static void maintainSurvivalDefense(ServerPlayer player, Anchor anchor) {
        if (anchor.survivalDefense == null || !anchor.survivalDefense.isActive()) {
            anchor.survivalDefense = SurvivalDefense.acquire(
                    player,
                    SurvivalDefenseProfile.absolute(1.0f)
            );
        }
        SurvivalDefense.repairNow(player);
    }

    private static void closeSurvivalDefense(Anchor anchor) {
        if (anchor == null || anchor.survivalDefense == null) return;
        anchor.survivalDefense.close();
        anchor.survivalDefense = null;
    }

    private static void maintainTemporalImmunity(ServerPlayer player, Anchor anchor) {
        if (!VectorReflection.Server.isActive(player)) {
            closeTemporalImmunity(anchor);
            return;
        }
        if (anchor.temporalImmunity != null && anchor.temporalImmunity.isActive()) return;
        anchor.temporalImmunity = TemporalApi.get(player)
                .acquireTimeStopImmunity(player);
    }

    private static void closeTemporalImmunity(Anchor anchor) {
        if (anchor == null || anchor.temporalImmunity == null) return;
        anchor.temporalImmunity.close();
        anchor.temporalImmunity = null;
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

    private static boolean isAcademyStateCaller(String entryMethod) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != VectorReflectionRuntime.class
                        || !frame.getMethodName().equals(entryMethod))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(VectorReflectionRuntime.class));
        if (caller == VectorReflectionRuntime.class) return true;
        try {
            CodeSource callerSource = caller.getProtectionDomain().getCodeSource();
            CodeSource academySource = AcademyCraft.class.getProtectionDomain().getCodeSource();
            return callerSource != null && academySource != null
                    && Objects.equals(callerSource.getLocation(), academySource.getLocation());
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private static final class Anchor {
        private volatile WeakReference<ServerPlayer> player;
        private volatile Vec3 lastSafePosition;
        private volatile boolean observerRebuildRequested;
        private volatile long lastObserverRebuildTick = Long.MIN_VALUE / 2L;
        private volatile long lastFailureLogNanos;
        private SurvivalDefenseLease survivalDefense;
        private TemporalImmunityLease temporalImmunity;

        private Anchor(ServerPlayer player) {
            this.player = new WeakReference<>(player);
            lastSafePosition = finite(player.position()) ? player.position() : Vec3.ZERO;
        }
    }
}
