package org.academy.internal.server.world.level.storage;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Per-tick power feed, coverage index, and force-crash countdown for {@link MisakaRelayRegistry}.
 */
final class MisakaRelayPowerTicker {
    private final MisakaRelayRegistry registry;

    MisakaRelayPowerTicker(MisakaRelayRegistry registry) {
        this.registry = registry;
    }

    /**
     * True if the satellite is powered for coverage, including a feed already queued this tick
     * (before {@link #endTick} flips the persistent {@code powered} flag).
     * Force-crash arming and crash phase reject power immediately.
     */
    boolean isReceivingPower(UUID satelliteId) {
        if (satelliteId == null || !acceptsPowerFeed(satelliteId)) {
            return false;
        }
        if (registry.fedThisTick.contains(satelliteId)) {
            return true;
        }
        var entry = registry.byId.get(satelliteId);
        return entry != null && entry.powered;
    }

    /**
     * Lasers may drain and call {@link MisakaRelayRegistry#feed} only while the satellite still accepts power.
     * Launch ascent, armed force-crash, and non-orbit phases refuse feed (no coverage / no beam).
     */
    boolean acceptsPowerFeed(UUID satelliteId) {
        if (satelliteId == null) {
            return false;
        }
        var entry = registry.byId.get(satelliteId);
        return entry != null
                && entry.phase == MisakaRelayEntry.Phase.ORBIT
                && entry.forceCrashCountdownTicks <= 0;
    }

    /**
     * True while the satellite is in orbit with unpaid crash debt ({@code unpoweredTicks > 0}).
     * Lasers should drain maintain + recovery energy and call {@link MisakaRelayRegistry#feed(UUID, boolean)} with recover.
     */
    boolean needsCrashRecovery(UUID satelliteId) {
        if (satelliteId == null || !acceptsPowerFeed(satelliteId)) {
            return false;
        }
        var entry = registry.byId.get(satelliteId);
        return entry != null && entry.unpoweredTicks > 0;
    }

    /** Maintain-only feed (coverage). Does not reduce crash debt. */
    void feed(UUID satelliteId) {
        feed(satelliteId, false);
    }

    /**
     * Queue a feed pulse for this tick.
     * @param recoverCrashDebt when true and the satellite has debt, {@link #endTick} reduces
     *                         {@code unpoweredTicks} by 1 (requires the laser to have paid extra drain).
     */
    void feed(UUID satelliteId, boolean recoverCrashDebt) {
        if (satelliteId != null && acceptsPowerFeed(satelliteId)) {
            registry.fedThisTick.add(satelliteId);
            if (recoverCrashDebt) {
                registry.recoveryFedThisTick.add(satelliteId);
            }
        }
    }

    void endTick(MinecraftServer server) {
        int crashTicks = crashTimeout(server);
        var toCrash = new ArrayList<UUID>();
        var forceDue = new ArrayList<UUID>();
        for (var entry : registry.byId.values()) {
            boolean forceArmed = entry.forceCrashCountdownTicks > 0;
            if (forceArmed) {
                if (!entry.phase.isActive()) {
                    entry.forceCrashCountdownTicks = 0;
                    forceArmed = false;
                } else {
                    entry.forceCrashCountdownTicks--;
                    if (entry.forceCrashCountdownTicks <= 0) {
                        forceDue.add(entry.satelliteId);
                    }
                }
            }
            if (!entry.phase.isActive()) {
                continue;
            }
            // Launch / force-crash own the timeline: stay dark, ignore feed, no unpowered timeout race.
            if (forceArmed || entry.phase == MisakaRelayEntry.Phase.LAUNCHING) {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                    registry.markPersistentDirty();
                }
                continue;
            }
            boolean fed = registry.fedThisTick.contains(entry.satelliteId);
            if (fed) {
                if (!entry.powered) {
                    entry.powered = true;
                    bumpPoweredCount(entry, +1);
                    registry.markPersistentDirty();
                }
                // Crash debt only heals when the laser paid recovery energy this tick (1 tick per tick).
                if (registry.recoveryFedThisTick.contains(entry.satelliteId) && entry.unpoweredTicks > 0) {
                    entry.unpoweredTicks--;
                }
            } else {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                    registry.markPersistentDirty();
                }
                entry.unpoweredTicks++;
                if (entry.unpoweredTicks >= crashTicks) {
                    toCrash.add(entry.satelliteId);
                }
            }
        }
        registry.fedThisTick.clear();
        registry.recoveryFedThisTick.clear();
        for (UUID id : forceDue) {
            registry.lifecycle.beginCrash(server, id);
        }
        for (UUID id : toCrash) {
            registry.lifecycle.beginCrash(server, id);
        }
        if (server != null && ++registry.respawnCheckTick >= 100) {
            registry.respawnCheckTick = 0;
            registry.lifecycle.tryRespawnMissing(server);
        }
    }

    /**
     * Arm a forced crash countdown for an active satellite. Fails if already crashing or already armed.
     * Cuts coverage power immediately; lasers stop feeding until cancel or crash completes.
     */
    boolean scheduleForceCrash(MinecraftServer server, UUID satelliteId, int ticks) {
        var entry = registry.get(satelliteId);
        if (entry == null || !entry.phase.isActive() || entry.forceCrashCountdownTicks > 0) {
            return false;
        }
        entry.forceCrashCountdownTicks = Math.max(1, ticks);
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
            registry.markPersistentDirty();
        }
        return true;
    }

    /**
     * Cancel a pending forced-crash countdown. Fails once {@link MisakaRelayEntry.Phase#CRASHING} has started.
     */
    boolean cancelForceCrash(MinecraftServer server, UUID satelliteId) {
        var entry = registry.get(satelliteId);
        if (entry == null || entry.phase == MisakaRelayEntry.Phase.CRASHING || entry.forceCrashCountdownTicks <= 0) {
            return false;
        }
        entry.forceCrashCountdownTicks = 0;
        return true;
    }

    private static int crashTimeout(MinecraftServer server) {
        if (server == null) {
            return 6000;
        }
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return 6000;
        }
        return Math.max(1, academy.getGenericConfig().misakaRelayCrashTicks);
    }

    void bumpPoweredCount(MisakaRelayEntry entry, int delta) {
        if (!entry.phase.isActive() || delta == 0) {
            return;
        }
        long key = MisakaRelayRegistry.netDimKey(entry.networkId, entry.dimension);
        int before = registry.poweredCountByNetDim.getOrDefault(key, 0);
        int after = before + delta;
        if (after <= 0) {
            registry.poweredCountByNetDim.remove(key);
            after = 0;
        } else {
            registry.poweredCountByNetDim.put(key, after);
        }
        if ((before == 0) != (after == 0)) {
            registry.markComputeDirty(null);
        }
    }

    /** Test hook: endTick with fixed crash timeout and no live server. */
    void testingEndTick(int crashTimeoutTicks) {
        int crashTicks = Math.max(1, crashTimeoutTicks);
        var toCrash = new ArrayList<UUID>();
        for (var entry : registry.byId.values()) {
            if (!entry.phase.isActive()) {
                continue;
            }
            if (entry.forceCrashCountdownTicks > 0 || entry.phase == MisakaRelayEntry.Phase.LAUNCHING) {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                }
                continue;
            }
            boolean fed = registry.fedThisTick.contains(entry.satelliteId);
            if (fed) {
                if (!entry.powered) {
                    entry.powered = true;
                    bumpPoweredCount(entry, +1);
                }
                if (registry.recoveryFedThisTick.contains(entry.satelliteId) && entry.unpoweredTicks > 0) {
                    entry.unpoweredTicks--;
                }
            } else {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                }
                entry.unpoweredTicks++;
                if (entry.unpoweredTicks >= crashTicks) {
                    toCrash.add(entry.satelliteId);
                }
            }
        }
        registry.fedThisTick.clear();
        registry.recoveryFedThisTick.clear();
        for (UUID id : toCrash) {
            registry.lifecycle.beginCrash(null, id);
        }
    }
}
