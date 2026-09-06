package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public enum Phase {
        ORBIT,
        LAUNCHING,
        CRASHING;

        public static final Codec<Phase> CODEC = Codec.STRING.xmap(
                name -> {
                    try {
                        return Phase.valueOf(name);
                    } catch (IllegalArgumentException ex) {
                        return ORBIT;
                    }
                },
                Enum::name
        );

        /** Orbiting or ascending — still registered; power / beam only after {@link #ORBIT}. */
        public boolean isActive() {
            return this == ORBIT || this == LAUNCHING;
        }
    }

    public static final class Entry {
        public final UUID satelliteId;
        public BlockPos networkId;
        public ResourceKey<Level> dimension;
        public boolean hyper;
        /**
         * Last laser tower position used as orbit anchor.
         * When {@link #laserBound} is false the tower was destroyed / unbound and no longer feeds power.
         */
        public BlockPos laserPos;
        public ResourceKey<Level> laserDimension;
        public BlockPos cabinPos;
        public @Nullable UUID entityUuid;
        public Phase phase = Phase.ORBIT;
        /** False after the bound energy laser tower is destroyed until a new unused tower is rebound. */
        public boolean laserBound = true;
        /** Runtime: fed this server tick. */
        public transient boolean powered;
        /**
         * Runtime crash-debt ticks: increments while unfed, decreases only when a laser pays
         * recovery energy ({@link #feed(UUID, boolean)} with recover). Does not reset instantly on power restore.
         */
        public transient int unpoweredTicks;
        /**
         * Runtime: ticks remaining before a cabin-ops forced crash fires.
         * {@code 0} means not armed; cancel clears it before {@link Phase#CRASHING}.
         */
        public transient int forceCrashCountdownTicks;

        public Entry(
                UUID satelliteId,
                BlockPos networkId,
                ResourceKey<Level> dimension,
                boolean hyper,
                BlockPos laserPos,
                ResourceKey<Level> laserDimension,
                BlockPos cabinPos,
                @Nullable UUID entityUuid,
                Phase phase
        ) {
            this(satelliteId, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, entityUuid, phase, true);
        }

        public Entry(
                UUID satelliteId,
                BlockPos networkId,
                ResourceKey<Level> dimension,
                boolean hyper,
                BlockPos laserPos,
                ResourceKey<Level> laserDimension,
                BlockPos cabinPos,
                @Nullable UUID entityUuid,
                Phase phase,
                boolean laserBound
        ) {
            this.satelliteId = satelliteId;
            this.networkId = networkId.immutable();
            this.dimension = dimension;
            this.hyper = hyper;
            this.laserPos = laserPos.immutable();
            this.laserDimension = laserDimension;
            this.cabinPos = cabinPos.immutable();
            this.entityUuid = entityUuid;
            this.phase = phase == null ? Phase.ORBIT : phase;
            this.laserBound = laserBound;
        }
    }

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );
    private static final Codec<BlockPos> BLOCK_POS_CODEC = Codec.STRING.flatXmap(
            value -> {
                try {
                    var parts = value.split(",");
                    if (parts.length != 3) {
                        return DataResult.error(() -> "Invalid BlockPos: " + value);
                    }
                    return DataResult.success(new BlockPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                    ));
                } catch (NumberFormatException exception) {
                    return DataResult.error(() -> "Invalid BlockPos: " + value);
                }
            },
            pos -> DataResult.success(pos.getX() + "," + pos.getY() + "," + pos.getZ())
    );
    private static final Codec<ResourceKey<Level>> DIMENSION_CODEC = Identifier.CODEC.flatXmap(
            id -> DataResult.success(ResourceKey.create(Registries.DIMENSION, id)),
            key -> DataResult.success(key.identifier())
    );

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("satellite_id").forGetter(e -> e.satelliteId),
            BLOCK_POS_CODEC.fieldOf("network_id").forGetter(e -> e.networkId),
            DIMENSION_CODEC.fieldOf("dimension").forGetter(e -> e.dimension),
            Codec.BOOL.fieldOf("hyper").forGetter(e -> e.hyper),
            BLOCK_POS_CODEC.fieldOf("laser_pos").forGetter(e -> e.laserPos),
            DIMENSION_CODEC.fieldOf("laser_dimension").forGetter(e -> e.laserDimension),
            BLOCK_POS_CODEC.fieldOf("cabin_pos").forGetter(e -> e.cabinPos),
            UUID_CODEC.optionalFieldOf("entity_uuid").forGetter(e -> Optional.ofNullable(e.entityUuid)),
            Phase.CODEC.fieldOf("phase").orElse(Phase.ORBIT).forGetter(e -> e.phase),
            Codec.BOOL.fieldOf("laser_bound").orElse(true).forGetter(e -> e.laserBound)
    ).apply(instance, (id, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, entityUuid, phase, laserBound) ->
            new Entry(id, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, entityUuid.orElse(null), phase, laserBound)
    ));

    public static final Codec<MisakaRelayRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(ENTRY_CODEC).fieldOf("satellites").forGetter(MisakaRelayRegistry::snapshotList)
    ).apply(instance, MisakaRelayRegistry::fromList));

    private final Map<UUID, Entry> byId = new HashMap<>();
    private final Map<Long, UUID> laserOwner = new HashMap<>();
    private final Map<Long, Integer> poweredCountByNetDim = new HashMap<>();
    private final Set<UUID> fedThisTick = new HashSet<>();
    /** Subset of {@link #fedThisTick}: paid extra energy this tick to reduce crash debt. */
    private final Set<UUID> recoveryFedThisTick = new HashSet<>();
    private int respawnCheckTick;
    private Runnable persistentDirty = () -> {};

    public MisakaRelayRegistry() {
    }

    private void bindPersistentDirty(Runnable callback) {
        persistentDirty = callback == null ? () -> {} : callback;
    }

    private void markPersistentDirty() {
        persistentDirty.run();
    }

    private static MisakaRelayRegistry fromList(List<Entry> entries) {
        var registry = new MisakaRelayRegistry();
        for (var entry : entries) {
            registry.byId.put(entry.satelliteId, entry);
            if (entry.laserBound) {
                registry.laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
            }
        }
        return registry;
    }

    private List<Entry> snapshotList() {
        return new ArrayList<>(byId.values());
    }

    public static MisakaRelayRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Persistence.TYPE).registry;
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

    private static long netDimKey(BlockPos networkId, ResourceKey<Level> dimension) {
        long dimHash = dimension == null ? 0L : (long) dimension.identifier().hashCode();
        return (networkId == null ? 0L : networkId.asLong()) ^ (dimHash * 31L);
    }

    public boolean hasActiveRelay(BlockPos networkId, ResourceKey<Level> dimension) {
        if (networkId == null || dimension == null) {
            return false;
        }
        return poweredCountByNetDim.getOrDefault(netDimKey(networkId, dimension), 0) > 0;
    }

    public @Nullable Entry get(UUID satelliteId) {
        return satelliteId == null ? null : byId.get(satelliteId);
    }

    public @Nullable UUID laserBoundSatellite(ResourceKey<Level> laserDim, BlockPos laserPos) {
        return laserOwner.get(laserKey(laserDim, laserPos));
    }

    public List<Entry> listByCabin(BlockPos cabinPos) {
        var list = new ArrayList<Entry>();
        if (cabinPos == null) {
            return list;
        }
        for (var entry : byId.values()) {
            if (cabinPos.equals(entry.cabinPos)) {
                list.add(entry);
            }
        }
        return list;
    }

    /** Satellites whose coverage topology is {@code networkId}. */
    public List<Entry> listByNetwork(BlockPos networkId) {
        var list = new ArrayList<Entry>();
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
    public List<Entry> listByDimension(ResourceKey<Level> dimension) {
        var list = new ArrayList<Entry>();
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
        if (satelliteId == null || !acceptsPowerFeed(satelliteId)) {
            return false;
        }
        if (fedThisTick.contains(satelliteId)) {
            return true;
        }
        var entry = byId.get(satelliteId);
        return entry != null && entry.powered;
    }

    /**
     * Lasers may drain and call {@link #feed} only while the satellite still accepts power.
     * Launch ascent, armed force-crash, and non-orbit phases refuse feed (no coverage / no beam).
     */
    public boolean acceptsPowerFeed(UUID satelliteId) {
        if (satelliteId == null) {
            return false;
        }
        var entry = byId.get(satelliteId);
        return entry != null
                && entry.phase == Phase.ORBIT
                && entry.forceCrashCountdownTicks <= 0;
    }

    public List<Entry> all() {
        return List.copyOf(byId.values());
    }

    /**
     * True while the satellite is in orbit with unpaid crash debt ({@code unpoweredTicks > 0}).
     * Lasers should drain maintain + recovery energy and call {@link #feed(UUID, boolean)} with recover.
     */
    public boolean needsCrashRecovery(UUID satelliteId) {
        if (satelliteId == null || !acceptsPowerFeed(satelliteId)) {
            return false;
        }
        var entry = byId.get(satelliteId);
        return entry != null && entry.unpoweredTicks > 0;
    }

    /** Maintain-only feed (coverage). Does not reduce crash debt. */
    public void feed(UUID satelliteId) {
        feed(satelliteId, false);
    }

    /**
     * Queue a feed pulse for this tick.
     * @param recoverCrashDebt when true and the satellite has debt, {@link #endTick} reduces
     *                         {@code unpoweredTicks} by 1 (requires the laser to have paid extra drain).
     */
    public void feed(UUID satelliteId, boolean recoverCrashDebt) {
        if (satelliteId != null && acceptsPowerFeed(satelliteId)) {
            fedThisTick.add(satelliteId);
            if (recoverCrashDebt) {
                recoveryFedThisTick.add(satelliteId);
            }
        }
    }

    public void endTick(MinecraftServer server) {
        int crashTicks = crashTimeout(server);
        var toCrash = new ArrayList<UUID>();
        var forceDue = new ArrayList<UUID>();
        for (var entry : byId.values()) {
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
            if (forceArmed || entry.phase == Phase.LAUNCHING) {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                    markPersistentDirty();
                }
                continue;
            }
            boolean fed = fedThisTick.contains(entry.satelliteId);
            if (fed) {
                if (!entry.powered) {
                    entry.powered = true;
                    bumpPoweredCount(entry, +1);
                    markPersistentDirty();
                }
                // Crash debt only heals when the laser paid recovery energy this tick (1 tick per tick).
                if (recoveryFedThisTick.contains(entry.satelliteId) && entry.unpoweredTicks > 0) {
                    entry.unpoweredTicks--;
                }
            } else {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                    markPersistentDirty();
                }
                entry.unpoweredTicks++;
                if (entry.unpoweredTicks >= crashTicks) {
                    toCrash.add(entry.satelliteId);
                }
            }
        }
        fedThisTick.clear();
        recoveryFedThisTick.clear();
        for (UUID id : forceDue) {
            beginCrash(server, id);
        }
        for (UUID id : toCrash) {
            beginCrash(server, id);
        }
        if (server != null && ++respawnCheckTick >= 100) {
            respawnCheckTick = 0;
            tryRespawnMissing(server);
        }
    }

    /**
     * Arm a forced crash countdown for an active satellite. Fails if already crashing or already armed.
     * Cuts coverage power immediately; lasers stop feeding until cancel or crash completes.
     */
    public boolean scheduleForceCrash(MinecraftServer server, UUID satelliteId, int ticks) {
        var entry = get(satelliteId);
        if (entry == null || !entry.phase.isActive() || entry.forceCrashCountdownTicks > 0) {
            return false;
        }
        entry.forceCrashCountdownTicks = Math.max(1, ticks);
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
            markPersistentDirty();
        }
        return true;
    }

    /**
     * Cancel a pending forced-crash countdown. Fails once {@link Phase#CRASHING} has started.
     */
    public boolean cancelForceCrash(MinecraftServer server, UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || entry.phase == Phase.CRASHING || entry.forceCrashCountdownTicks <= 0) {
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

    private void bumpPoweredCount(Entry entry, int delta) {
        if (!entry.phase.isActive() || delta == 0) {
            return;
        }
        long key = netDimKey(entry.networkId, entry.dimension);
        int before = poweredCountByNetDim.getOrDefault(key, 0);
        int after = before + delta;
        if (after <= 0) {
            poweredCountByNetDim.remove(key);
            after = 0;
        } else {
            poweredCountByNetDim.put(key, after);
        }
        if ((before == 0) != (after == 0)) {
            MisakaComputeIndex.get().markDirty();
        }
    }

    public boolean launch(
            MinecraftServer server,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos
    ) {
        if (server == null || networkId == null || dimension == null || laserPos == null || laserDimension == null || cabinPos == null) {
            return false;
        }
        long lk = laserKey(laserDimension, laserPos);
        if (laserOwner.containsKey(lk)) {
            return false;
        }
        UUID satelliteId = UUID.randomUUID();
        var entry = new Entry(
                satelliteId,
                networkId,
                dimension,
                hyper,
                laserPos,
                laserDimension,
                cabinPos,
                null,
                Phase.LAUNCHING
        );
        byId.put(satelliteId, entry);
        laserOwner.put(lk, satelliteId);
        markPersistentDirty();
        spawnEntity(server, entry, true);
        MisakaComputeIndex.get().markDirty();
        return true;
    }

    public boolean retargetNetwork(MinecraftServer server, UUID satelliteId, BlockPos newNetworkId) {
        var entry = get(satelliteId);
        if (entry == null || newNetworkId == null || !entry.phase.isActive()) {
            return false;
        }
        if (entry.networkId.equals(newNetworkId.immutable())) {
            return true;
        }
        boolean wasPowered = entry.powered;
        if (wasPowered) {
            bumpPoweredCount(entry, -1);
        }
        entry.networkId = newNetworkId.immutable();
        if (wasPowered) {
            bumpPoweredCount(entry, +1);
        }
        markPersistentDirty();
        MisakaComputeIndex.get().markDirty();
        return true;
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
        var entry = get(satelliteId);
        if (entry == null || newLaserPos == null || newLaserDimension == null || !entry.phase.isActive()) {
            return false;
        }
        long newKey = laserKey(newLaserDimension, newLaserPos.immutable());
        UUID occupying = laserOwner.get(newKey);
        if (occupying != null && !occupying.equals(satelliteId)) {
            return false;
        }
        if (entry.laserBound
                && entry.laserDimension.equals(newLaserDimension)
                && entry.laserPos.equals(newLaserPos.immutable())) {
            return true;
        }
        if (entry.laserBound) {
            laserOwner.remove(laserKey(entry.laserDimension, entry.laserPos));
        }
        entry.laserPos = newLaserPos.immutable();
        entry.laserDimension = newLaserDimension;
        entry.laserBound = true;
        laserOwner.put(newKey, satelliteId);
        markPersistentDirty();
        MisakaComputeIndex.get().markDirty();
        moveOrbitAnchor(server, entry);
        return true;
    }

    /**
     * Launch ascent finished: satellite has reached its scheduled orbit slot.
     * Orbit phase is registry-only — discard any cosmetic launch entity.
     */
    public void completeLaunch(MinecraftServer server, UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || entry.phase != Phase.LAUNCHING) {
            return;
        }
        entry.phase = Phase.ORBIT;
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
        entry.entityUuid = null;
        markPersistentDirty();
    }

    /**
     * Satellite's bound energy laser tower was destroyed: clear laser ownership and drop into the
     * normal unpowered countdown ({@code misakaRelayCrashTicks}). Stay in orbit until rebound or timeout crash.
     */
    public void unbindLaser(MinecraftServer server, UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || !entry.laserBound) {
            return;
        }
        laserOwner.remove(laserKey(entry.laserDimension, entry.laserPos));
        entry.laserBound = false;
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
        }
        // Enter / continue the same unpowered countdown used when a laser stops feeding.
        markPersistentDirty();
        MisakaComputeIndex.get().markDirty();
    }

    public void beginCrash(MinecraftServer server, UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || entry.phase == Phase.CRASHING) {
            return;
        }
        entry.forceCrashCountdownTicks = 0;
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
        }
        entry.phase = Phase.CRASHING;
        markPersistentDirty();
        MisakaComputeIndex.get().markDirty();

        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity == null) {
            entity = spawnCrashEntity(server, entry);
        }
        if (entity != null) {
            entity.beginCrash();
        } else {
            completeCrash(server, satelliteId);
        }
    }

    public void completeCrash(MinecraftServer server, UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null) {
            return;
        }
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
        }
        if (entry.laserBound) {
            laserOwner.remove(laserKey(entry.laserDimension, entry.laserPos));
        }
        byId.remove(satelliteId);
        markPersistentDirty();
        MisakaComputeIndex.get().markDirty();
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    public void onLaserRemoved(MinecraftServer server, ResourceKey<Level> laserDim, BlockPos laserPos) {
        UUID id = laserOwner.get(laserKey(laserDim, laserPos));
        if (id != null) {
            unbindLaser(server, id);
        }
    }

    private void moveOrbitAnchor(MinecraftServer server, Entry entry) {
        if (server == null) {
            return;
        }
        RelaySatelliteEntity entity = findEntity(server, entry);
        if (entity == null) {
            // ORBIT has no resident entity; LAUNCHING/CRASHING keep their own spawn paths.
            return;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
    }

    private void tryRespawnMissing(MinecraftServer server) {
        for (var entry : byId.values()) {
            if (entry.phase == Phase.ORBIT) {
                continue;
            }
            if (entry.phase == Phase.LAUNCHING) {
                if (findEntity(server, entry) == null) {
                    // Unload mid-ascent: snap to abstract orbit, do not replay launch or spawn.
                    entry.phase = Phase.ORBIT;
                    entry.entityUuid = null;
                    markPersistentDirty();
                }
                continue;
            }
            if (entry.phase == Phase.CRASHING) {
                if (findEntity(server, entry) != null) {
                    continue;
                }
                var spawned = spawnCrashEntity(server, entry);
                if (spawned == null) {
                    completeCrash(server, entry.satelliteId);
                } else {
                    spawned.beginCrash();
                }
            }
        }
    }

    private void spawnEntity(MinecraftServer server, Entry entry, boolean withLaunchAnim) {
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        var entity = new RelaySatelliteEntity(level);
        entity.setSatelliteId(entry.satelliteId);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
        entity.setHyper(entry.hyper);

        if (withLaunchAnim) {
            var start = resolveLaunchStart(level, entry, orbitY);
            int chunkX = BlockPos.containing(start).getX() >> 4;
            int chunkZ = BlockPos.containing(start).getZ() >> 4;
            if (!level.hasChunk(chunkX, chunkZ)) {
                // Cannot play ascent — abstract orbit immediately, no resident entity.
                entry.phase = Phase.ORBIT;
                entry.entityUuid = null;
                markPersistentDirty();
                return;
            }
            entity.setPos(start.x, start.y, start.z);
            entity.beginLaunch();
        } else {
            // Non-launch spawns are only for crash recovery.
            int chunkX = entry.laserPos.getX() >> 4;
            int chunkZ = entry.laserPos.getZ() >> 4;
            if (!level.hasChunk(chunkX, chunkZ)) {
                return;
            }
            entity.setPos(
                    entry.laserPos.getX() + 0.5 + RelaySatelliteEntity.ORBIT_RADIUS,
                    orbitY,
                    entry.laserPos.getZ() + 0.5
            );
        }

        level.addFreshEntity(entity);
        entry.entityUuid = entity.getUUID();
        markPersistentDirty();
    }

    private @Nullable RelaySatelliteEntity spawnCrashEntity(MinecraftServer server, Entry entry) {
        if (server == null) {
            return null;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return null;
        }
        int chunkX = entry.laserPos.getX() >> 4;
        int chunkZ = entry.laserPos.getZ() >> 4;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        double orbitY = MisakaRelayOrbits.visualOrbitY(level, server);
        var entity = new RelaySatelliteEntity(level);
        entity.setSatelliteId(entry.satelliteId);
        entity.setOrbitAnchor(entry.laserPos.getX() + 0.5, orbitY, entry.laserPos.getZ() + 0.5);
        entity.setHyper(entry.hyper);
        entity.setPos(
                entry.laserPos.getX() + 0.5 + RelaySatelliteEntity.ORBIT_RADIUS,
                orbitY,
                entry.laserPos.getZ() + 0.5
        );
        level.addFreshEntity(entity);
        entry.entityUuid = entity.getUUID();
        markPersistentDirty();
        return entity;
    }

    private static Vec3 resolveLaunchStart(
            net.minecraft.server.level.ServerLevel level,
            Entry entry,
            double orbitY
    ) {
        if (entry.dimension.equals(entry.laserDimension)) {
            var cabinChunkX = entry.cabinPos.getX() >> 4;
            var cabinChunkZ = entry.cabinPos.getZ() >> 4;
            if (level.hasChunk(cabinChunkX, cabinChunkZ)) {
                return new Vec3(
                        entry.cabinPos.getX() + 0.5,
                        entry.cabinPos.getY() + 2.0,
                        entry.cabinPos.getZ() + 0.5
                );
            }
        }
        double startY = Math.min(entry.laserPos.getY() + 2.0, orbitY - 32.0);
        startY = Math.max(startY, level.dimensionType().minY() + 8.0);
        return new Vec3(entry.laserPos.getX() + 0.5, startY, entry.laserPos.getZ() + 0.5);
    }

    private static @Nullable RelaySatelliteEntity findEntity(MinecraftServer server, Entry entry) {
        if (server == null || entry.entityUuid == null) {
            return null;
        }
        var level = server.getLevel(entry.dimension);
        if (level == null) {
            return null;
        }
        var entity = level.getEntity(entry.entityUuid);
        return entity instanceof RelaySatelliteEntity sat ? sat : null;
    }

    /** Test hook: finish launch without a live entity / server. */
    public void testingCompleteLaunch(UUID satelliteId) {
        completeLaunch(null, satelliteId);
    }

    /**
     * Test hook: enter {@link Phase#CRASHING} without a live server spawn.
     * Mirrors the pre-spawn side of {@link #beginCrash} when no resident entity exists.
     */
    public void testingBeginCrashPhaseOnly(UUID satelliteId) {
        var entry = get(satelliteId);
        if (entry == null || entry.phase == Phase.CRASHING) {
            return;
        }
        if (entry.powered) {
            entry.powered = false;
            bumpPoweredCount(entry, -1);
        }
        entry.phase = Phase.CRASHING;
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
    public void testingPutPowered(Entry entry) {
        byId.put(entry.satelliteId, entry);
        entry.laserBound = true;
        laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
        entry.powered = true;
        entry.phase = Phase.ORBIT;
        bumpPoweredCount(entry, +1);
    }

    /** Test hook: insert unpowered orbit entry without touching poweredCount. */
    public void testingPutOrbit(Entry entry) {
        byId.put(entry.satelliteId, entry);
        entry.laserBound = true;
        laserOwner.put(laserKey(entry.laserDimension, entry.laserPos), entry.satelliteId);
        entry.powered = false;
        entry.phase = Phase.ORBIT;
        entry.unpoweredTicks = 0;
    }

    /** Test hook: endTick with fixed crash timeout and no live server. */
    public void testingEndTick(int crashTimeoutTicks) {
        int crashTicks = Math.max(1, crashTimeoutTicks);
        var toCrash = new ArrayList<UUID>();
        for (var entry : byId.values()) {
            if (!entry.phase.isActive()) {
                continue;
            }
            if (entry.forceCrashCountdownTicks > 0 || entry.phase == Phase.LAUNCHING) {
                if (entry.powered) {
                    entry.powered = false;
                    bumpPoweredCount(entry, -1);
                }
                continue;
            }
            boolean fed = fedThisTick.contains(entry.satelliteId);
            if (fed) {
                if (!entry.powered) {
                    entry.powered = true;
                    bumpPoweredCount(entry, +1);
                }
                if (recoveryFedThisTick.contains(entry.satelliteId) && entry.unpoweredTicks > 0) {
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
        fedThisTick.clear();
        recoveryFedThisTick.clear();
        for (UUID id : toCrash) {
            beginCrash(null, id);
        }
    }
}
