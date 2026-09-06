package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaRelayRegistryTest {
    /** Avoid {@code Level.OVERWORLD} — that static touches FML AttachmentHolder in unit tests. */
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));

    private final MisakaRelayRegistry registry = new MisakaRelayRegistry();

    @AfterEach
    void clear() {
        registry.testingClear();
        MisakaComputeIndex.get().testingClearAggregates();
    }

    @Test
    void poweredSatelliteGrantsActiveRelay() {
        var networkId = new BlockPos(10, 64, 10);
        var entry = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(0, 70, 0));
        registry.testingPutPowered(entry);
        assertTrue(registry.hasActiveRelay(networkId, OVERWORLD));
        assertFalse(registry.hasActiveRelay(networkId, NETHER));
        assertFalse(registry.hasActiveRelay(new BlockPos(99, 0, 99), OVERWORLD));
    }

    @Test
    void multiSatelliteSameNetDimStaysActiveUntilLastPoweredOff() {
        var networkId = new BlockPos(1, 2, 3);
        var a = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(0, 70, 0));
        var b = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(1, 70, 0));
        registry.testingPutPowered(a);
        registry.testingPutPowered(b);
        assertTrue(registry.hasActiveRelay(networkId, OVERWORLD));

        registry.feed(a.satelliteId);
        registry.testingEndTick(6000);
        assertTrue(registry.hasActiveRelay(networkId, OVERWORLD));

        registry.testingEndTick(6000);
        assertFalse(registry.hasActiveRelay(networkId, OVERWORLD));
    }

    @Test
    void laserOneToOneOccupancy() {
        var laser = new BlockPos(5, 80, 5);
        var first = orbitEntry(UUID.randomUUID(), new BlockPos(1, 1, 1), OVERWORLD, laser);
        registry.testingPutOrbit(first);
        assertEquals(first.satelliteId, registry.laserBoundSatellite(OVERWORLD, laser));
    }

    @Test
    void retargetMovesCoverageBetweenNetworks() {
        var oldNet = new BlockPos(1, 0, 0);
        var newNet = new BlockPos(2, 0, 0);
        var entry = orbitEntry(UUID.randomUUID(), oldNet, OVERWORLD, new BlockPos(3, 70, 3));
        registry.testingPutPowered(entry);
        assertTrue(registry.hasActiveRelay(oldNet, OVERWORLD));
        assertFalse(registry.hasActiveRelay(newNet, OVERWORLD));

        assertTrue(registry.retargetNetwork(null, entry.satelliteId, newNet));
        assertFalse(registry.hasActiveRelay(oldNet, OVERWORLD));
        assertTrue(registry.hasActiveRelay(newNet, OVERWORLD));
    }

    @Test
    void unpoweredTimeoutCrashesWithoutLootAndClearsRegistry() {
        var networkId = new BlockPos(7, 7, 7);
        var entry = orbitEntry(UUID.randomUUID(), networkId, NETHER, new BlockPos(8, 80, 8));
        entry.hyper = true;
        registry.testingPutPowered(entry);
        assertTrue(registry.hasActiveRelay(networkId, NETHER));

        registry.testingEndTick(2);
        assertFalse(registry.hasActiveRelay(networkId, NETHER));
        assertEquals(1, registry.all().size());

        // No loaded entity → beginCrash → completeCrash (discard-only path; RenderOnlyEntity never drops items).
        registry.testingEndTick(2);
        assertTrue(registry.all().isEmpty());
        assertFalse(registry.hasActiveRelay(networkId, NETHER));
    }

    @Test
    void allowsMultipleSatellitesSameNetworkAndDimension() {
        var networkId = new BlockPos(4, 4, 4);
        registry.testingPutOrbit(orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(0, 1, 0)));
        registry.testingPutOrbit(orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(0, 1, 1)));
        assertEquals(2, registry.all().size());
    }

    @Test
    void poweredCountEdgeOnlyMarksComputeDirty() {
        var networkId = new BlockPos(9, 9, 9);
        var index = MisakaComputeIndex.get();
        index.testingSetDirty(false);

        var a = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(0, 70, 0));
        registry.testingPutPowered(a);
        assertTrue(index.testingIsDirty(), "0→1 powered edge must dirty compute index");
        index.testingSetDirty(false);

        var b = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, new BlockPos(1, 70, 0));
        registry.testingPutPowered(b);
        assertFalse(index.testingIsDirty(), "1→2 powered must not dirty");

        registry.feed(a.satelliteId);
        registry.feed(b.satelliteId);
        registry.testingEndTick(6000);
        assertFalse(index.testingIsDirty(), "stay powered must not dirty");

        registry.feed(a.satelliteId);
        registry.testingEndTick(6000);
        assertFalse(index.testingIsDirty(), "2→1 powered must not dirty");
        assertTrue(registry.hasActiveRelay(networkId, OVERWORLD));

        registry.testingEndTick(6000);
        assertTrue(index.testingIsDirty(), "1→0 powered edge must dirty");
        assertFalse(registry.hasActiveRelay(networkId, OVERWORLD));
    }

    @Test
    void laserDestroyedUnbindsWithoutImmediateCrash() {
        var networkId = new BlockPos(11, 64, 11);
        var laser = new BlockPos(20, 80, 20);
        var entry = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, laser);
        registry.testingPutPowered(entry);
        assertTrue(registry.hasActiveRelay(networkId, OVERWORLD));
        assertEquals(entry.satelliteId, registry.laserBoundSatellite(OVERWORLD, laser));

        registry.onLaserRemoved(null, OVERWORLD, laser);
        assertFalse(entry.laserBound);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);
        assertEquals(1, registry.all().size());
        assertEquals(null, registry.laserBoundSatellite(OVERWORLD, laser));
        assertFalse(registry.hasActiveRelay(networkId, OVERWORLD));
    }

    @Test
    void rebindLaserMovesOwnershipToNewTower() {
        var networkId = new BlockPos(12, 64, 12);
        var oldLaser = new BlockPos(1, 80, 1);
        var newLaser = new BlockPos(2, 80, 2);
        var entry = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, oldLaser);
        registry.testingPutOrbit(entry);
        registry.onLaserRemoved(null, OVERWORLD, oldLaser);
        assertFalse(entry.laserBound);

        assertTrue(registry.rebindLaser(null, entry.satelliteId, newLaser, OVERWORLD));
        assertTrue(entry.laserBound);
        assertEquals(newLaser, entry.laserPos);
        assertEquals(null, registry.laserBoundSatellite(OVERWORLD, oldLaser));
        assertEquals(entry.satelliteId, registry.laserBoundSatellite(OVERWORLD, newLaser));
    }

    @Test
    void rebindLaserRejectsOccupiedTower() {
        var networkId = new BlockPos(13, 64, 13);
        var laserA = new BlockPos(3, 80, 3);
        var laserB = new BlockPos(4, 80, 4);
        var first = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, laserA);
        var second = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, laserB);
        registry.testingPutOrbit(first);
        registry.testingPutOrbit(second);
        registry.onLaserRemoved(null, OVERWORLD, laserA);

        assertFalse(registry.rebindLaser(null, first.satelliteId, laserB, OVERWORLD));
        assertFalse(first.laserBound);
        assertEquals(second.satelliteId, registry.laserBoundSatellite(OVERWORLD, laserB));
    }

    @Test
    void laserDestroyedEntersUnpoweredCountdownThenCrashes() {
        var networkId = new BlockPos(14, 64, 14);
        var laser = new BlockPos(30, 80, 30);
        var entry = orbitEntry(UUID.randomUUID(), networkId, OVERWORLD, laser);
        registry.testingPutPowered(entry);
        registry.onLaserRemoved(null, OVERWORLD, laser);
        assertFalse(entry.laserBound);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);

        registry.testingEndTick(2);
        assertEquals(1, registry.all().size());
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);

        registry.testingEndTick(2);
        assertTrue(registry.all().isEmpty());
    }

    private static MisakaRelayRegistry.Entry orbitEntry(
            UUID id,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            BlockPos laserPos
    ) {
        return new MisakaRelayRegistry.Entry(
                id,
                networkId,
                dimension,
                false,
                laserPos,
                OVERWORLD,
                new BlockPos(100, 64, 100),
                null,
                MisakaRelayRegistry.Phase.ORBIT
        );
    }
}
