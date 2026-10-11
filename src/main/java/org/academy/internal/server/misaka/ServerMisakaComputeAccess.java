package org.academy.internal.server.misaka;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaComputeAccess;
import org.academy.api.common.misaka.MisakaComputeDevice;
import org.academy.api.common.misaka.MisakaComputeDevices;
import org.academy.api.common.misaka.MisakaComputePresence;
import org.academy.api.common.misaka.MisakaComputeSpendMode;
import org.academy.internal.server.world.level.storage.MisakaPlayerDeviceAllocations;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production Misaka compute access: player budgets, device effective %, ACTUAL pool, FORCE delivery.
 * ACTUAL {@link #consumeRate} queues per tick; multi-caller grants use weighted flush
 * ({@link MisakaComputeWindowMath}); single caller may consume the whole remaining pool.
 */
public final class ServerMisakaComputeAccess implements MisakaComputeAccess {
    public static final ServerMisakaComputeAccess INSTANCE = new ServerMisakaComputeAccess();

    private static final float EPS = 1.0e-5f;

    /** Last settle window budget MSk/s by player UUID. */
    private final ConcurrentHashMap<UUID, Float> budgetMskPerSec = new ConcurrentHashMap<>();

    /** Runtime effective % for present devices (player → device → %). */
    private final ConcurrentHashMap<UUID, Map<Identifier, Integer>> effectiveByPlayer = new ConcurrentHashMap<>();

    /** Previous present set for become-present detection. */
    private final ConcurrentHashMap<UUID, Set<Identifier>> previouslyPresent = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Float> actualRemaining = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<Identifier, Float>> forceReserved = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Identifier>> touchedThisTick = new ConcurrentHashMap<>();
    /** Pending ACTUAL wants this tick (device → want MSk). Insertion order = call order. */
    private final ConcurrentHashMap<UUID, LinkedHashMap<Identifier, Float>> pendingCalls = new ConcurrentHashMap<>();
    /** Pool snapshot when the first pending call of the tick was recorded. */
    private final ConcurrentHashMap<UUID, Float> pendingPoolStart = new ConcurrentHashMap<>();
    /** Absolute grants for the current tick after last flush. */
    private final ConcurrentHashMap<UUID, Map<Identifier, Float>> grantedThisTick = new ConcurrentHashMap<>();
    /** ACTUAL pool size recorded when the current window was opened (before spends). */
    private final ConcurrentHashMap<UUID, Float> actualShareAtOpen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Identifier, Long> lastErrorLogTick = new ConcurrentHashMap<>();

    private final ThreadLocal<Boolean> reentrantOnCompute = ThreadLocal.withInitial(() -> false);

    private @Nullable MinecraftServer server;

    private ServerMisakaComputeAccess() {
    }

    public void bindServer(MinecraftServer server) {
        this.server = server;
    }

    public void clearServer() {
        this.server = null;
        budgetMskPerSec.clear();
        effectiveByPlayer.clear();
        previouslyPresent.clear();
        actualRemaining.clear();
        forceReserved.clear();
        touchedThisTick.clear();
        pendingCalls.clear();
        pendingPoolStart.clear();
        grantedThisTick.clear();
        actualShareAtOpen.clear();
    }

    public void setPlayerBudget(UUID playerUuid, float mskPerSec) {
        if (playerUuid == null) {
            return;
        }
        if (!(mskPerSec > 0.0f) || !Float.isFinite(mskPerSec)) {
            budgetMskPerSec.remove(playerUuid);
        } else {
            budgetMskPerSec.put(playerUuid, mskPerSec);
        }
    }

    public void clearBudgets() {
        budgetMskPerSec.clear();
    }

    public boolean hasDeviceConsumer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        refreshPresence(player);
        var effective = effectiveByPlayer.get(player.getUUID());
        if (effective == null || effective.isEmpty()) {
            return false;
        }
        for (int value : effective.values()) {
            if (value > 0) {
                return true;
            }
        }
        var stored = MisakaPlayerDeviceAllocations.get(player.level().getServer());
        return stored.neverConfigured(player.getUUID()) && !MisakaDevicePresence.presentDevices(player).isEmpty();
    }

    /**
     * Scan presence, flush ACTUAL pending grants, clear per-tick touch sets.
     * Call every server tick (after item-use ticks so pending calls are complete).
     */
    public void tickPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        refreshPresence(player);
        flushPendingCalls(player);
        commitActualBurn(player);
        MisakaOrbitalCharge.finalizeTickGrant(player);
        touchedThisTick.remove(player.getUUID());
        pendingCalls.remove(player.getUUID());
        pendingPoolStart.remove(player.getUUID());
        grantedThisTick.remove(player.getUUID());
    }

    /** Close previous window (FORCE onCompute) then open a new window from budgets. */
    public void rollWindows(MinecraftServer server) {
        closeAllWindows(server);
        openAllWindows(server);
    }

    public void closeAllWindows(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            flushPendingCalls(player);
            closeWindow(player);
        }
    }

    public void openAllWindows(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPresence(player);
            openWindow(player);
        }
    }

    @Override
    public float consumeRate(ServerPlayer player, Identifier deviceId, float mskPerSecond) {
        if (Boolean.TRUE.equals(reentrantOnCompute.get())) {
            return 0.0f;
        }
        if (player == null || deviceId == null) {
            return 0.0f;
        }
        var device = MisakaComputeDevices.get(deviceId);
        if (device == null || device.spendMode() != MisakaComputeSpendMode.ACTUAL) {
            return 0.0f;
        }
        if (!Float.isFinite(mskPerSecond) || mskPerSecond <= 0.0f) {
            return 0.0f;
        }
        refreshPresence(player);
        if (!MisakaDevicePresence.isPresent(player, device)) {
            return 0.0f;
        }
        if (device.presence() == MisakaComputePresence.HELD
                && !MisakaDevicePresence.isActivelyUsing(player, device)) {
            return 0.0f;
        }
        boolean neverConfigured = MisakaPlayerDeviceAllocations.get(player.level().getServer())
                .neverConfigured(player.getUUID());
        int effective = effectivePercent(player, deviceId);
        if (effective <= 0 && !neverConfigured) {
            return 0.0f;
        }
        // neverConfigured + stale 0%: rebuild equal-split before opening the ACTUAL pool.
        if (effective <= 0 && neverConfigured) {
            refreshPresence(player);
            effective = effectivePercent(player, deviceId);
            if (effective <= 0) {
                return 0.0f;
            }
        }
        ensureActualWindow(player);
        var touched = touchedThisTick.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        if (!touched.add(deviceId)) {
            return 0.0f;
        }
        float want = mskPerSecond / MisakaComputeContribution.SETTLE_INTERVAL_TICKS;
        if (!(want > EPS)) {
            return 0.0f;
        }
        UUID uuid = player.getUUID();
        pendingPoolStart.computeIfAbsent(uuid, ignored -> actualRemaining.getOrDefault(uuid, 0.0f));
        pendingCalls.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>()).put(deviceId, want);
        flushPendingCalls(player);
        return grantedThisTick.getOrDefault(uuid, Map.of()).getOrDefault(deviceId, 0.0f);
    }

    /**
     * Open the ACTUAL/FORCE window if settle already assigned a budget but no window was opened yet
     * (e.g. first second after login, or presence appeared after the last settle).
     * If a window was opened with a zero ACTUAL pool and ACTUAL devices later gained share,
     * credit the missing share into the pool without waiting for the next settle.
     */
    private void ensureActualWindow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float budget = budgetMskPerSec.getOrDefault(uuid, 0.0f);
        if (!(budget > EPS)) {
            return;
        }
        if (!actualRemaining.containsKey(uuid) && !forceReserved.containsKey(uuid)) {
            openWindow(player);
            return;
        }
        reconcileActualPool(player);
    }

    /**
     * Rebuild FORCE/ACTUAL pools from the current budget and effective % without delivering FORCE.
     * Used when ActiveNetwork or device budget changes mid settle-window.
     */
    public void reopenWindow(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        flushPendingCalls(player);
        pendingCalls.remove(uuid);
        pendingPoolStart.remove(uuid);
        grantedThisTick.remove(uuid);
        touchedThisTick.remove(uuid);
        actualRemaining.remove(uuid);
        forceReserved.remove(uuid);
        actualShareAtOpen.remove(uuid);
        refreshPresence(player);
        openWindow(player);
    }

    /** Absolute ACTUAL grant for {@code deviceId} after the latest flush this tick. */
    public float grantedThisTick(ServerPlayer player, Identifier deviceId) {
        if (player == null || deviceId == null) {
            return 0.0f;
        }
        return grantedThisTick.getOrDefault(player.getUUID(), Map.of()).getOrDefault(deviceId, 0.0f);
    }

    private void flushPendingCalls(ServerPlayer player) {
        UUID uuid = player.getUUID();
        var pending = pendingCalls.get(uuid);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        float pool = pendingPoolStart.getOrDefault(uuid, actualRemaining.getOrDefault(uuid, 0.0f));
        var calls = new ArrayList<MisakaComputeWindowMath.Call>(pending.size());
        for (var entry : pending.entrySet()) {
            calls.add(new MisakaComputeWindowMath.Call(
                    entry.getKey(),
                    entry.getValue(),
                    Math.max(0, effectivePercent(player, entry.getKey()))
            ));
        }
        var grants = MisakaComputeWindowMath.flushActualCalls(pool, calls);
        float used = MisakaComputeWindowMath.totalGranted(grants);
        actualRemaining.put(uuid, Math.max(0.0f, pool - used));
        var grantMap = new HashMap<Identifier, Float>();
        for (var grant : grants) {
            grantMap.put(grant.id(), grant.amount());
        }
        grantedThisTick.put(uuid, grantMap);
        // Net ACTUAL burn for this settle window is recomputed at tick end; mid-tick flushes
        // only update remaining/grants maps. Burn is applied once in tickPlayer after flush.
    }

    /** Commit ACTUAL grants into the settle-window burn ledger (call once per tick after final flush). */
    void commitActualBurn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float total = 0.0f;
        var grants = grantedThisTick.get(uuid);
        if (grants != null) {
            for (float amount : grants.values()) {
                total += Math.max(0.0f, amount);
            }
        }
        if (total > EPS) {
            MisakaComputeUsageTracker.addChargeUsage(uuid, total);
        }
    }

    @Override
    public float playerBudgetMskPerSecond(ServerPlayer player) {
        if (player == null) {
            return 0.0f;
        }
        return budgetMskPerSec.getOrDefault(player.getUUID(), 0.0f);
    }

    @Override
    public int effectivePercent(ServerPlayer player, Identifier deviceId) {
        if (player == null || deviceId == null) {
            return 0;
        }
        var map = effectiveByPlayer.get(player.getUUID());
        if (map == null) {
            return 0;
        }
        return map.getOrDefault(deviceId, 0);
    }

    @Override
    public int lastPercent(ServerPlayer player, Identifier deviceId) {
        if (player == null || deviceId == null || server == null) {
            return 0;
        }
        return MisakaPlayerDeviceAllocations.get(server).lastPercent(player.getUUID(), deviceId);
    }

    @Override
    public int remainingPercentSlots(ServerPlayer player) {
        if (player == null) {
            return 100;
        }
        int sum = 0;
        var map = effectiveByPlayer.get(player.getUUID());
        if (map != null) {
            for (int value : map.values()) {
                sum += Math.max(0, value);
            }
        }
        return Math.max(0, 100 - sum);
    }

    @Override
    public float cpPerMsk(MinecraftServer server) {
        return MisakaComputeContribution.cpPerMsk(server);
    }

    public Map<Identifier, Integer> effectiveSnapshot(ServerPlayer player) {
        if (player == null) {
            return Map.of();
        }
        var map = effectiveByPlayer.get(player.getUUID());
        return map == null ? Map.of() : Map.copyOf(map);
    }

    public void setDeviceAllocations(ServerPlayer player, Map<Identifier, Integer> percents) {
        if (player == null || server == null) {
            return;
        }
        MisakaPlayerDeviceAllocations.get(server).setLastPercents(player.getUUID(), percents);
        var present = MisakaDevicePresence.presentDevices(player);
        var next = new LinkedHashMap<Identifier, Integer>();
        int sum = 0;
        for (var device : present) {
            int last = MisakaPlayerDeviceAllocations.get(server).lastPercent(player.getUUID(), device.id());
            if (last <= 0) {
                continue;
            }
            int value = Math.min(last, 100 - sum);
            if (value > 0) {
                next.put(device.id(), value);
                sum += value;
            }
        }
        effectiveByPlayer.put(player.getUUID(), next);
        previouslyPresent.put(player.getUUID(), new HashSet<>(next.keySet()));
        for (var device : present) {
            if (!next.containsKey(device.id())) {
                int last = MisakaPlayerDeviceAllocations.get(server).lastPercent(player.getUUID(), device.id());
                if (last > 0) {
                    player.sendOverlayMessage(Component.translatable(
                            "message.academy.misaka_device_quota_insufficient",
                            device.displayName(),
                            last,
                            100 - sum
                    ));
                }
            }
        }
        MisakaDeviceQuotaSync.sync(player);
        reopenWindow(player);
    }

    private void refreshPresence(ServerPlayer player) {
        if (server == null) {
            return;
        }
        UUID uuid = player.getUUID();
        var present = MisakaDevicePresence.presentDevices(player);
        var presentIds = new HashSet<Identifier>();
        for (var device : present) {
            presentIds.add(device.id());
        }
        var prev = previouslyPresent.getOrDefault(uuid, Set.of());
        var stored = MisakaPlayerDeviceAllocations.get(server);
        boolean syncNeeded = false;

        if (stored.neverConfigured(uuid)) {
            var next = equalSplit(present);
            effectiveByPlayer.put(uuid, next);
            previouslyPresent.put(uuid, presentIds);
            return;
        }

        var current = new LinkedHashMap<>(effectiveByPlayer.getOrDefault(uuid, Map.of()));
        current.keySet().removeIf(id -> !presentIds.contains(id));

        for (var device : present) {
            Identifier id = device.id();
            if (prev.contains(id)) {
                continue;
            }
            int last = stored.lastPercent(uuid, id);
            if (last <= 0) {
                current.putIfAbsent(id, 0);
                continue;
            }
            int others = sumExcept(current, id);
            if (others + last <= 100) {
                current.put(id, last);
            } else {
                current.put(id, 0);
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.misaka_device_quota_insufficient",
                        device.displayName(),
                        last,
                        Math.max(0, 100 - others)
                ));
                syncNeeded = true;
            }
        }

        for (var device : present) {
            Identifier id = device.id();
            if (!current.containsKey(id)) {
                current.put(id, 0);
            }
        }

        effectiveByPlayer.put(uuid, current);
        previouslyPresent.put(uuid, presentIds);
        if (syncNeeded) {
            MisakaDeviceQuotaSync.sync(player);
        }
    }

    /**
     * If ACTUAL device share grew after the window opened (new presence / equal-split),
     * credit only the delta into {@link #actualRemaining}. Does not refill spent pool.
     */
    private void reconcileActualPool(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float budget = budgetMskPerSec.getOrDefault(uuid, 0.0f);
        if (!(budget > EPS)) {
            return;
        }
        float expectedActual = 0.0f;
        var expectedForce = new HashMap<Identifier, Float>();
        for (var device : MisakaDevicePresence.presentDevices(player)) {
            int percent = effectivePercent(player, device.id());
            if (percent <= 0) {
                continue;
            }
            float share = budget * (percent / 100.0f);
            if (!(share > 0.0f)) {
                continue;
            }
            if (device.spendMode() == MisakaComputeSpendMode.FORCE) {
                expectedForce.put(device.id(), share);
            } else {
                expectedActual += share;
            }
        }
        float opened = actualShareAtOpen.getOrDefault(uuid, 0.0f);
        if (expectedActual > opened + EPS) {
            float delta = expectedActual - opened;
            actualRemaining.merge(uuid, delta, Float::sum);
            actualShareAtOpen.put(uuid, expectedActual);
        }
        if (!expectedForce.isEmpty() || forceReserved.containsKey(uuid)) {
            forceReserved.put(uuid, expectedForce);
        }
        if (!actualRemaining.containsKey(uuid)) {
            actualRemaining.put(uuid, Math.max(0.0f, expectedActual - opened));
            actualShareAtOpen.putIfAbsent(uuid, expectedActual);
        }
    }

    private static Map<Identifier, Integer> equalSplit(List<MisakaComputeDevice> present) {
        var next = new LinkedHashMap<Identifier, Integer>();
        int n = present.size();
        if (n == 0) {
            return next;
        }
        int base = 100 / n;
        int rem = 100 - base * n;
        for (int i = 0; i < present.size(); i++) {
            int value = base + (i == 0 ? rem : 0);
            if (value > 0) {
                next.put(present.get(i).id(), value);
            }
        }
        return next;
    }

    private static int sumExcept(Map<Identifier, Integer> map, Identifier except) {
        int sum = 0;
        for (var entry : map.entrySet()) {
            if (entry.getKey().equals(except)) {
                continue;
            }
            sum += Math.max(0, entry.getValue());
        }
        return sum;
    }

    private void openWindow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float budget = budgetMskPerSec.getOrDefault(uuid, 0.0f);
        var force = new HashMap<Identifier, Float>();
        float actualSum = 0.0f;
        for (var device : MisakaDevicePresence.presentDevices(player)) {
            int percent = effectivePercent(player, device.id());
            if (percent <= 0) {
                continue;
            }
            float share = budget * (percent / 100.0f);
            if (!(share > 0.0f)) {
                continue;
            }
            if (device.spendMode() == MisakaComputeSpendMode.FORCE) {
                force.put(device.id(), share);
            } else {
                actualSum += share;
            }
        }
        forceReserved.put(uuid, force);
        actualRemaining.put(uuid, actualSum);
        actualShareAtOpen.put(uuid, actualSum);
    }

    private void closeWindow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        float leftover = Math.max(0.0f, actualRemaining.getOrDefault(uuid, 0.0f));
        var reserved = forceReserved.getOrDefault(uuid, Map.of());
        var forces = new ArrayList<MisakaComputeDevice>();
        for (var device : MisakaDevicePresence.presentDevices(player)) {
            if (device.spendMode() == MisakaComputeSpendMode.FORCE && reserved.containsKey(device.id())) {
                forces.add(device);
            }
        }
        int weightSum = 0;
        for (var device : forces) {
            weightSum += Math.max(0, effectivePercent(player, device.id()));
        }
        for (var device : forces) {
            float base = reserved.getOrDefault(device.id(), 0.0f);
            float total = MisakaComputeWindowMath.forceDelivery(
                    base,
                    leftover,
                    effectivePercent(player, device.id()),
                    weightSum
            );
            if (!(total > EPS)) {
                continue;
            }
            deliverForce(player, device, total);
            MisakaComputeUsageTracker.addChargeUsage(player.getUUID(), total);
        }
        actualRemaining.remove(uuid);
        forceReserved.remove(uuid);
        actualShareAtOpen.remove(uuid);
    }

    private void deliverForce(ServerPlayer player, MisakaComputeDevice device, float msk) {
        reentrantOnCompute.set(true);
        try {
            device.deliverForce(player, msk);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError) {
                throw (VirtualMachineError) t;
            }
            long tick = server == null ? 0L : server.getTickCount();
            Long last = lastErrorLogTick.get(device.id());
            if (last == null || tick / MisakaComputeContribution.SETTLE_INTERVAL_TICKS
                    != last / MisakaComputeContribution.SETTLE_INTERVAL_TICKS) {
                lastErrorLogTick.put(device.id(), tick);
                AcademyCraft.getLogger().warn(
                        "Misaka FORCE device {} onCompute failed: {}",
                        device.id(),
                        t.toString()
                );
            }
        } finally {
            reentrantOnCompute.set(false);
        }
    }
}
