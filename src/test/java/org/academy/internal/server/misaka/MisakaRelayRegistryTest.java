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

    @Test
    void completeLaunchClearsEntityUuidAndStaysOrbit() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(15, 64, 15), OVERWORLD, new BlockPos(40, 80, 40));
        entry.phase = MisakaRelayRegistry.Phase.LAUNCHING;
        entry.entityUuid = UUID.randomUUID();
        registry.testingPutOrbit(entry);
        entry.phase = MisakaRelayRegistry.Phase.LAUNCHING;

        registry.testingCompleteLaunch(entry.satelliteId);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);
        assertEquals(null, entry.entityUuid);
    }

    @Test
    void launchingRefusesFeedUntilOrbit() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(20, 64, 20), OVERWORLD, new BlockPos(45, 80, 45));
        registry.testingPutOrbit(entry);
        entry.phase = MisakaRelayRegistry.Phase.LAUNCHING;

        assertFalse(registry.acceptsPowerFeed(entry.satelliteId));
        registry.feed(entry.satelliteId);
        registry.testingEndTick(2);
        assertFalse(entry.powered);
        assertEquals(MisakaRelayRegistry.Phase.LAUNCHING, entry.phase);
        assertFalse(registry.hasActiveRelay(entry.networkId, OVERWORLD));

        registry.testingCompleteLaunch(entry.satelliteId);
        assertTrue(registry.acceptsPowerFeed(entry.satelliteId));
        registry.feed(entry.satelliteId);
        registry.endTick(null);
        assertTrue(entry.powered);
        assertTrue(registry.hasActiveRelay(entry.networkId, OVERWORLD));
    }

    @Test
    void beginCrashWithoutEntityEntersCrashingViaHook() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(16, 64, 16), OVERWORLD, new BlockPos(41, 80, 41));
        registry.testingPutOrbit(entry);
        entry.entityUuid = null;

        registry.testingBeginCrashPhaseOnly(entry.satelliteId);
        assertEquals(MisakaRelayRegistry.Phase.CRASHING, entry.phase);
        assertEquals(null, entry.entityUuid);
        assertEquals(entry, registry.get(entry.satelliteId));
    }

    @Test
    void beginCrashWithoutEntityStillClearsRegistryWhenServerMissing() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(16, 64, 16), OVERWORLD, new BlockPos(41, 80, 41));
        registry.testingPutOrbit(entry);
        entry.entityUuid = null;

        registry.beginCrash(null, entry.satelliteId);
        // No server → spawnCrashEntity fails → completeCrash removes entry.
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void forceCrashCountdownCanBeArmedAndCancelled() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(17, 64, 17), OVERWORLD, new BlockPos(42, 80, 42));
        registry.testingPutOrbit(entry);

        assertTrue(registry.scheduleForceCrash(null, entry.satelliteId, 5));
        assertEquals(5, entry.forceCrashCountdownTicks);
        assertFalse(registry.scheduleForceCrash(null, entry.satelliteId, 5));

        assertTrue(registry.cancelForceCrash(null, entry.satelliteId));
        assertEquals(0, entry.forceCrashCountdownTicks);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);
        assertFalse(registry.cancelForceCrash(null, entry.satelliteId));
    }

    @Test
    void forceCrashCutsPowerImmediatelyAndRefusesFeed() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(19, 64, 19), OVERWORLD, new BlockPos(44, 80, 44));
        registry.testingPutPowered(entry);
        assertTrue(entry.powered);
        assertTrue(registry.hasActiveRelay(entry.networkId, OVERWORLD));

        assertTrue(registry.scheduleForceCrash(null, entry.satelliteId, 10));
        assertFalse(entry.powered);
        assertFalse(registry.acceptsPowerFeed(entry.satelliteId));
        assertFalse(registry.hasActiveRelay(entry.networkId, OVERWORLD));
        assertFalse(registry.isReceivingPower(entry.satelliteId));

        registry.feed(entry.satelliteId);
        registry.endTick(null);
        assertFalse(entry.powered);
        assertEquals(9, entry.forceCrashCountdownTicks);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);
        assertFalse(registry.hasActiveRelay(entry.networkId, OVERWORLD));
    }

    @Test
    void forceCrashCountdownExpiryBeginsCrash() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(18, 64, 18), OVERWORLD, new BlockPos(43, 80, 43));
        registry.testingPutOrbit(entry);
        assertTrue(registry.scheduleForceCrash(null, entry.satelliteId, 2));

        registry.endTick(null);
        assertEquals(1, entry.forceCrashCountdownTicks);
        assertEquals(MisakaRelayRegistry.Phase.ORBIT, entry.phase);

        registry.endTick(null);
        // beginCrash(null) with no entity → completeCrash removes entry.
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void maintainFeedDoesNotClearCrashDebtInstantly() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(19, 64, 19), OVERWORLD, new BlockPos(44, 80, 44));
        registry.testingPutPowered(entry);

        registry.testingEndTick(6000);
        assertEquals(1, entry.unpoweredTicks);
        assertFalse(entry.powered);
        assertTrue(registry.needsCrashRecovery(entry.satelliteId));

        registry.feed(entry.satelliteId, false);
        registry.testingEndTick(6000);
        assertTrue(entry.powered);
        assertEquals(1, entry.unpoweredTicks);
        assertTrue(registry.needsCrashRecovery(entry.satelliteId));
    }

    @Test
    void recoveryFeedHealsCrashDebtOneTickPerTick() {
        var entry = orbitEntry(UUID.randomUUID(), new BlockPos(21, 64, 21), OVERWORLD, new BlockPos(46, 80, 46));
        registry.testingPutOrbit(entry);
        entry.unpoweredTicks = 3;
        assertTrue(registry.needsCrashRecovery(entry.satelliteId));

        registry.feed(entry.satelliteId, true);
        registry.testingEndTick(6000);
        assertTrue(entry.powered);
        assertEquals(2, entry.unpoweredTicks);

        registry.feed(entry.satelliteId, true);
        registry.testingEndTick(6000);
        assertEquals(1, entry.unpoweredTicks);

        registry.feed(entry.satelliteId, true);
        registry.testingEndTick(6000);
        assertEquals(0, entry.unpoweredTicks);
        assertFalse(registry.needsCrashRecovery(entry.satelliteId));

        registry.feed(entry.satelliteId, true);
        registry.testingEndTick(6000);
        assertEquals(0, entry.unpoweredTicks);
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
