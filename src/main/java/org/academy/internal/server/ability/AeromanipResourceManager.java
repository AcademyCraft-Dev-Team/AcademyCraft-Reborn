package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.aeromanip.Aeromanip;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AirAccessResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the fixed-capacity compressed-air MP pool used by Aeromanipulation. */
public final class AeromanipResourceManager implements AbilitySubsystem {
    private static final float EPSILON = 1.0e-4f;
    private static final int MAX_CONFIGURED_CAPACITY = 1_000_000;
    private static final float MAX_CONFIGURED_RECOVERY = 1_000_000.0f;

    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final SyncManager syncManager;
    private final Map<UUID, Integer> activeUsageCounts = new HashMap<>();
    private final Map<UUID, Long> lastUseTicks = new HashMap<>();

    public AeromanipResourceManager(
            PlayerDataManager playerDataManager,
            PlayerCPManager playerCPManager,
            SyncManager syncManager
    ) {
        this.playerDataManager = playerDataManager;
        this.playerCPManager = playerCPManager;
        this.syncManager = syncManager;
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        reconcile(player, false);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        var uuid = player.getUUID();
        activeUsageCounts.remove(uuid);
        lastUseTicks.remove(uuid);
    }

    @Override
    public void tick(ServerPlayer player) {
        reconcile(player, true);
    }

    @Override
    public void processSync(ServerPlayer player) {
        // Compressed air is mirrored through SyncAbilityDataPacket/CP_DATA.
    }

    public boolean supportsCompressedAir(ServerPlayer player) {
        return player != null
                && playerDataManager.getPlayerAbilityCategory(player.getUUID())
                == AbilityCategories.AEROMANIP.get();
    }

    public float getCurrent(ServerPlayer player) {
        var data = playerDataManager.getData(player.getUUID());
        return data == null ? 0.0f : data.getCpData().getCurrMP();
    }

    public float getCapacity(ServerPlayer player) {
        return configuredCapacity(player);
    }

    public boolean tryConsume(ServerPlayer player, float amount) {
        if (!Float.isFinite(amount) || amount < 0.0f || !supportsCompressedAir(player)) return false;
        reconcile(player, false);
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return false;
        var cpData = data.getCpData();
        if (cpData.getCurrMP() + EPSILON < amount) return false;
        if (amount > 0.0f) cpData.setCurrMP(Math.max(0.0f, cpData.getCurrMP() - amount));
        commitUse(player, data);
        return true;
    }

    /** Atomically pays a timed CP charge and compressed-air cost. */
    public boolean tryCast(
            ServerPlayer player,
            Skill skill,
            float actualCpCost,
            float compressedAirCost,
            int iterationTicks,
            String stackGroup,
            int stackLimit
    ) {
        if (skill == null || !supportsCompressedAir(player)
                || !Float.isFinite(actualCpCost) || actualCpCost < 0.0f
                || !Float.isFinite(compressedAirCost) || compressedAirCost < 0.0f) return false;
        reconcile(player, false);
        if (!playerCPManager.tryOccupationAndConsumeMP(
                player.getUUID(), actualCpCost, compressedAirCost, skill,
                iterationTicks, false, stackGroup, stackLimit)) return false;
        var data = playerDataManager.getData(player.getUUID());
        if (data != null) commitUse(player, data);
        return true;
    }

    public UsageLease beginUse(ServerPlayer player) {
        var uuid = player.getUUID();
        activeUsageCounts.merge(uuid, 1, Integer::sum);
        markUsed(player);
        return new UsageLease(this, uuid);
    }

    public void markUsed(ServerPlayer player) {
        lastUseTicks.put(player.getUUID(), player.level().getGameTime());
    }

    public boolean isInUse(ServerPlayer player) {
        return activeUsageCounts.getOrDefault(player.getUUID(), 0) > 0;
    }

    private void reconcile(ServerPlayer player, boolean allowRecovery) {
        if (!supportsCompressedAir(player)) return;
        var data = playerDataManager.getData(player.getUUID());
        if (data == null) return;
        var cpData = data.getCpData();
        var capacity = configuredCapacity(player);
        var changed = false;

        var previousCapacity = cpData.getMaxMP();
        if (Math.abs(previousCapacity - capacity) > EPSILON) {
            var current = previousCapacity <= EPSILON
                    ? capacity
                    : Math.min(cpData.getCurrMP(), capacity);
            cpData.setMaxMP(capacity);
            cpData.setCurrMP(current);
            changed = true;
        } else if (cpData.getCurrMP() > capacity + EPSILON) {
            cpData.setCurrMP(capacity);
            changed = true;
        }

        var gameTime = player.level().getGameTime();
        var canRecover = allowRecovery
                && !isInUse(player)
                && lastUseTicks.getOrDefault(player.getUUID(), Long.MIN_VALUE) < gameTime
                && AirAccessResolver.hasAmbientAir(player);
        var recovered = recover(
                cpData.getCurrMP(), capacity, configuredRecovery(player), canRecover);
        if (recovered > cpData.getCurrMP() + EPSILON) {
            cpData.setCurrMP(recovered);
            changed = true;
        }

        if (changed) {
            data.markDirty();
            syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
        }
    }

    private void commitUse(ServerPlayer player, org.academy.internal.server.world.level.storage.Player data) {
        markUsed(player);
        data.markDirty();
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
    }

    private float configuredCapacity(ServerPlayer player) {
        return normalizeCapacity(AeromanipConfig.settings(player).compressedAirCapacity);
    }

    private float configuredRecovery(ServerPlayer player) {
        return normalizeRecovery(AeromanipConfig.settings(player).compressedAirRecoveryPerTick);
    }

    static int normalizeCapacity(int capacity) {
        return Math.clamp(capacity, 0, MAX_CONFIGURED_CAPACITY);
    }

    static float normalizeRecovery(float recovery) {
        if (!Float.isFinite(recovery)) return Aeromanip.DEFAULT_COMPRESSED_AIR_RECOVERY_PER_TICK;
        return Math.clamp(recovery, 0.0f, MAX_CONFIGURED_RECOVERY);
    }

    static float recover(float current, float capacity, float recovery, boolean allowed) {
        if (!Float.isFinite(current) || !Float.isFinite(capacity) || !Float.isFinite(recovery)) return 0.0f;
        var safeCapacity = Math.max(0.0f, capacity);
        var safeCurrent = Math.clamp(current, 0.0f, safeCapacity);
        if (!allowed || recovery <= 0.0f) return safeCurrent;
        return Math.min(safeCapacity, safeCurrent + recovery);
    }

    private void endUse(UUID uuid) {
        var count = activeUsageCounts.getOrDefault(uuid, 0);
        if (count <= 1) activeUsageCounts.remove(uuid);
        else activeUsageCounts.put(uuid, count - 1);
    }

    public static final class UsageLease implements AutoCloseable {
        private final AeromanipResourceManager manager;
        private final UUID playerId;
        private boolean closed;

        private UsageLease(AeromanipResourceManager manager, UUID playerId) {
            this.manager = manager;
            this.playerId = playerId;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            manager.endUse(playerId);
        }
    }
}
