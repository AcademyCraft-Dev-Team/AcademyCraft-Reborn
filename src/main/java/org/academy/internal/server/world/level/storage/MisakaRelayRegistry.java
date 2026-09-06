package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative registry of launched Misaka relay satellites.
 * Persisted via overworld {@link Persistence} SavedData.
 * Coverage queries use {@link #hasActiveRelay} O(1) via poweredCount index.
 */
public final class MisakaRelayRegistry {
    /** Cabin-ops forced crash arming delay (60 seconds). */
    public static final int FORCE_CRASH_COUNTDOWN_TICKS = 20 * 60;

    public static final Codec<MisakaRelayRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(MisakaRelayEntry.ENTRY_CODEC).fieldOf("satellites").forGetter(MisakaRelayRegistry::snapshotList)
    ).apply(instance, MisakaRelayRegistry::fromList));

    final Map<UUID, MisakaRelayEntry> byId = new HashMap<>();
    final Map<Long, UUID> laserOwner = new HashMap<>();
    final Map<Long, Integer> poweredCountByNetDim = new HashMap<>();
    final Set<UUID> fedThisTick = new HashSet<>();
    /** Subset of {@link #fedThisTick}: paid extra energy this tick to reduce crash debt. */
    final Set<UUID> recoveryFedThisTick = new HashSet<>();
    int respawnCheckTick;
    final MisakaRelayLifecycle lifecycle = new MisakaRelayLifecycle(this);
    final MisakaRelayPowerTicker power = new MisakaRelayPowerTicker(this);
    private Runnable persistentDirty = () -> {};
    private @Nullable transient MinecraftServer owningServer;

    public MisakaRelayRegistry() {
    }

    private void bindPersistentDirty(Runnable callback) {
        persistentDirty = callback == null ? () -> {} : callback;
    }

    void markPersistentDirty() {
        persistentDirty.run();
    }

    void markComputeDirty(@Nullable MinecraftServer server) {
        var override = MisakaComputeIndex.TESTING_OVERRIDE.get();
        if (override != null) {
            override.markDirty();
            return;
        }
        var resolved = server != null ? server : owningServer;
        if (resolved != null) {
            MisakaComputeIndex.get(resolved).markDirty();
        }
    }

    private static MisakaRelayRegistry fromList(List<MisakaRelayEntry> entries) {
        var registry = new MisakaRelayRegistry();
        for (var entry : entries) {
            registry.byId.put(entry.satelliteId, entry);
            if (entry.laserBound) {
                registry.laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
            }
        }
        return registry;
    }

    private List<MisakaRelayEntry> snapshotList() {
        return new ArrayList<>(byId.values());
    }

    public static MisakaRelayRegistry get(MinecraftServer server) {
        var registry = server.overworld().getDataStorage().computeIfAbsent(Persistence.TYPE).registry;
        registry.owningServer = server;
        return registry;
    }

    /** Overworld SavedData shell so unit tests can construct {@link MisakaRelayRegistry} without FML. */
    private static final class Persistence extends SavedData {
        private static final Codec<Persistence> CODEC = MisakaRelayRegistry.CODEC.xmap(
                Persistence::new,
                holder -> holder.registry
        );
        private static final SavedDataType<Persistence> TYPE = new SavedDataType<>(
                AcademyCraft.academy("misaka_relay_satellites"),
                Persistence::new,
                CODEC
        );

        private final MisakaRelayRegistry registry;

        private Persistence() {
            this(new MisakaRelayRegistry());
        }

        private Persistence(MisakaRelayRegistry registry) {
            this.registry = registry;
            registry.bindPersistentDirty(this::setDirty);
        }
    }

    public static long laserKey(ResourceKey<Level> dimension, BlockPos pos) {
        long dimHash = dimension == null ? 0L : dimension.identifier().hashCode();
        return (dimHash << 32) ^ (pos == null ? 0L : pos.asLong());
    }

    static long netDimKey(BlockPos networkId, ResourceKey<Level> dimension) {
        long dimHash = dimension == null ? 0L : (long) dimension.identifier().hashCode();
        return (networkId == null ? 0L : networkId.asLong()) ^ (dimHash * 31L);
    }

    public boolean hasActiveRelay(BlockPos networkId, ResourceKey<Level> dimension) {
        if (networkId == null || dimension == null) {
            return false;
        }
        return poweredCountByNetDim.getOrDefault(netDimKey(networkId, dimension), 0) > 0;
    }

    public @Nullable MisakaRelayEntry get(UUID satelliteId) {
        return satelliteId == null ? null : byId.get(satelliteId);
    }

    public @Nullable UUID laserBoundSatellite(ResourceKey<Level> laserDim, BlockPos laserPos) {
        return laserOwner.get(laserKey(laserDim, laserPos));
    }

    public List<MisakaRelayEntry> listByCabin(ResourceKey<Level> cabinDimension, BlockPos cabinPos) {
        var list = new ArrayList<MisakaRelayEntry>();
        if (cabinPos == null || cabinDimension == null) {
            return list;
        }
        for (var entry : byId.values()) {
            if (cabinPos.equals(entry.cabinPos) && cabinDimension.equals(entry.cabinDimension)) {
                list.add(entry);
            }
        }
        return list;
    }

    /** @deprecated use {@link #listByCabin(ResourceKey, BlockPos)} */
    @Deprecated
    public List<MisakaRelayEntry> listByCabin(BlockPos cabinPos) {
        return listByCabin(MisakaSavedDataCodecs.DEFAULT_OVERWORLD, cabinPos);
    }

    /** Satellites whose coverage topology is {@code networkId}. */
    public List<MisakaRelayEntry> listByNetwork(BlockPos networkId) {
        var list = new ArrayList<MisakaRelayEntry>();
        if (networkId == null) {
            return list;
        }
        var key = networkId.immutable();
        for (var entry : byId.values()) {
            if (key.equals(entry.networkId)) {
                list.add(entry);
            }
        }
        return list;
    }

    /** Satellites whose coverage/orbit dimension is {@code dimension}. */
    public List<MisakaRelayEntry> listByDimension(ResourceKey<Level> dimension) {
        var list = new ArrayList<MisakaRelayEntry>();
        if (dimension == null) {
            return list;
        }
        for (var entry : byId.values()) {
            if (dimension.equals(entry.dimension)) {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * True if the satellite is powered for coverage, including a feed already queued this tick
     * (before {@link #endTick} flips the persistent {@code powered} flag).
     * Force-crash arming and crash phase reject power immediately.
     */
    public boolean isReceivingPower(UUID satelliteId) {
        return power.isReceivingPower(satelliteId);
    }

    /**
     * Lasers may drain and call {@link #feed} only while the satellite still accepts power.
     * Launch ascent, armed force-crash, and non-orbit phases refuse feed (no coverage / no beam).
     */
    public boolean acceptsPowerFeed(UUID satelliteId) {
        return power.acceptsPowerFeed(satelliteId);
    }

    public List<MisakaRelayEntry> all() {
        return List.copyOf(byId.values());
    }

    /**
     * True while the satellite is in orbit with unpaid crash debt ({@code unpoweredTicks > 0}).
     * Lasers should drain maintain + recovery energy and call {@link #feed(UUID, boolean)} with recover.
     */
    public boolean needsCrashRecovery(UUID satelliteId) {
        return power.needsCrashRecovery(satelliteId);
    }

    /** Maintain-only feed (coverage). Does not reduce crash debt. */
    public void feed(UUID satelliteId) {
        power.feed(satelliteId);
    }

    /**
     * Queue a feed pulse for this tick.
     * @param recoverCrashDebt when true and the satellite has debt, {@link #endTick} reduces
     *                         {@code unpoweredTicks} by 1 (requires the laser to have paid extra drain).
     */
    public void feed(UUID satelliteId, boolean recoverCrashDebt) {
        power.feed(satelliteId, recoverCrashDebt);
    }

    public void endTick(MinecraftServer server) {
        power.endTick(server);
    }

    /**
     * Arm a forced crash countdown for an active satellite. Fails if already crashing or already armed.
     * Cuts coverage power immediately; lasers stop feeding until cancel or crash completes.
     */
    public boolean scheduleForceCrash(MinecraftServer server, UUID satelliteId, int ticks) {
        return power.scheduleForceCrash(server, satelliteId, ticks);
    }

    /**
     * Cancel a pending forced-crash countdown. Fails once {@link MisakaRelayEntry.Phase#CRASHING} has started.
     */
    public boolean cancelForceCrash(MinecraftServer server, UUID satelliteId) {
        return power.cancelForceCrash(server, satelliteId);
    }

    public boolean launch(
            MinecraftServer server,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos,
            ResourceKey<Level> cabinDimension
    ) {
        return lifecycle.launch(server, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, cabinDimension);
    }

    public boolean retargetNetwork(MinecraftServer server, UUID satelliteId, BlockPos newNetworkId) {
        return lifecycle.retargetNetwork(server, satelliteId, newNetworkId);
    }

    /**
     * Rebind an orbiting satellite to a free energy laser tower (e.g. after its previous tower was destroyed).
     * Coverage network is unchanged; orbit anchor moves to the new tower.
     */
    public boolean rebindLaser(
            MinecraftServer server,
            UUID satelliteId,
            BlockPos newLaserPos,
            ResourceKey<Level> newLaserDimension
    ) {
        return lifecycle.rebindLaser(server, satelliteId, newLaserPos, newLaserDimension);
    }

    /**
     * Launch ascent finished: satellite has reached its scheduled orbit slot.
     * Orbit phase is registry-only — discard any cosmetic launch entity.
     */
    public void completeLaunch(MinecraftServer server, UUID satelliteId) {
        lifecycle.completeLaunch(server, satelliteId);
    }

    /**
     * Satellite's bound energy laser tower was destroyed: clear laser ownership and drop into the
     * normal unpowered countdown ({@code misakaRelayCrashTicks}). Stay in orbit until rebound or timeout crash.
     */
    public void unbindLaser(MinecraftServer server, UUID satelliteId) {
        lifecycle.unbindLaser(server, satelliteId);
    }

    public void beginCrash(MinecraftServer server, UUID satelliteId) {
        lifecycle.beginCrash(server, satelliteId);
    }

    public void completeCrash(MinecraftServer server, UUID satelliteId) {
        lifecycle.completeCrash(server, satelliteId);
    }

    public void onLaserRemoved(MinecraftServer server, ResourceKey<Level> laserDim, BlockPos laserPos) {
        lifecycle.onLaserRemoved(server, laserDim, laserPos);
    }

    /** Test hook: finish launch without a live entity / server. */
    public void testingCompleteLaunch(UUID satelliteId) {
        completeLaunch(null, satelliteId);
    }

    /**
     * Test hook: enter {@link MisakaRelayEntry.Phase#CRASHING} without a live server spawn.
     * Mirrors the pre-spawn side of {@link #beginCrash} when no resident entity exists.
     */
    public void testingBeginCrashPhaseOnly(UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || entry.phase == MisakaRelayEntry.Phase.CRASHING) {
            return;
        }
        if (entry.powered) {
            entry.powered = false;
            power.bumpPoweredCount(entry, -1);
        }
        entry.phase = MisakaRelayEntry.Phase.CRASHING;
        entry.entityUuid = null;
    }

    /** Test hook: clear runtime indexes without SavedData. */
    public void testingClear() {
        byId.clear();
        laserOwner.clear();
        poweredCountByNetDim.clear();
        fedThisTick.clear();
        recoveryFedThisTick.clear();
        respawnCheckTick = 0;
    }

    /** Test hook: insert powered orbit entry. */
    public void testingPutPowered(MisakaRelayEntry entry) {
        byId.put(entry.satelliteId, entry);
        entry.laserBound = true;
        laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
        entry.powered = true;
        entry.phase = MisakaRelayEntry.Phase.ORBIT;
        power.bumpPoweredCount(entry, +1);
    }

    /** Test hook: insert unpowered orbit entry without touching poweredCount. */
    public void testingPutOrbit(MisakaRelayEntry entry) {
        byId.put(entry.satelliteId, entry);
        entry.laserBound = true;
        laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
        entry.powered = false;
        entry.phase = MisakaRelayEntry.Phase.ORBIT;
        entry.unpoweredTicks = 0;
    }

    /** Test hook: endTick with fixed crash timeout and no live server. */
    public void testingEndTick(int crashTimeoutTicks) {
        power.testingEndTick(crashTimeoutTicks);
    }
}
