package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import org.academy.api.common.misaka.MisakaRelayAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaNetworkCoverageTest {
    @AfterEach
    void clear() {
        MisakaNetworkCoverage.invalidate();
        MisakaRelayAccess.testingInstall(null);
    }

    @Test
    void sphereContainsInsideAndOutside() {
        var sphere = new MisakaNetworkCoverage.Sphere(new BlockPos(0, 64, 0), 100.0);
        assertTrue(sphere.contains(new BlockPos(0, 64, 0)));
        assertTrue(sphere.contains(new BlockPos(5, 64, 5)));
        assertFalse(sphere.contains(new BlockPos(20, 64, 0)));
    }

    @Test
    void zeroRadiusOnlyCoversNodeBlock() {
        var sphere = new MisakaNetworkCoverage.Sphere(new BlockPos(10, 0, 10), 0.0);
        assertTrue(sphere.contains(new BlockPos(10, 0, 10)));
        assertFalse(sphere.contains(new BlockPos(11, 0, 10)));
    }

    @Test
    void onlyOwnNetworkSpheresGrantCoverage() {
        var networkA = List.of(
                new MisakaNetworkCoverage.Sphere(new BlockPos(0, 64, 0), 25.0),
                new MisakaNetworkCoverage.Sphere(new BlockPos(4, 64, 0), 25.0)
        );
        var networkB = List.of(
                new MisakaNetworkCoverage.Sphere(new BlockPos(100, 64, 100), 400.0)
        );
        var sample = new BlockPos(2, 64, 0);
        assertTrue(networkA.stream().anyMatch(s -> s.contains(sample)));
        assertFalse(networkB.stream().anyMatch(s -> s.contains(sample)));
    }

    @Test
    void overworldEnergyFootprintGrantsWithoutRelay() {
        assertTrue(MisakaNetworkCoverage.resolveServiceAccess(true, true, false));
        assertFalse(MisakaNetworkCoverage.resolveServiceAccess(true, false, false));
    }

    @Test
    void overworldOutsideEnergyUsesRelay() {
        assertTrue(MisakaNetworkCoverage.resolveServiceAccess(true, false, true));
        assertFalse(MisakaNetworkCoverage.resolveServiceAccess(true, false, false));
    }

    @Test
    void nonOverworldIgnoresEnergyFootprint() {
        // Nether / hyper: energy spheres never apply; only powered relay grants.
        assertFalse(MisakaNetworkCoverage.resolveServiceAccess(false, true, false));
        assertTrue(MisakaNetworkCoverage.resolveServiceAccess(false, true, true));
        assertFalse(MisakaNetworkCoverage.resolveServiceAccess(false, false, false));
    }

    @Test
    void nullSampleLevelDefersToRelayAccess() {
        MisakaRelayAccess.testingInstall((level, pos, networkId) -> true);
        assertTrue(MisakaNetworkCoverage.canUseMisakaService(null, BlockPos.ZERO, BlockPos.ZERO));
        MisakaRelayAccess.testingInstall((level, pos, networkId) -> false);
        assertFalse(MisakaNetworkCoverage.canUseMisakaService(null, BlockPos.ZERO, BlockPos.ZERO));
    }

    @Test
    void liveRelayAccessDeniesWithoutLevel() {
        assertFalse(MisakaRelayAccess.get().grantsAccess(null, BlockPos.ZERO, BlockPos.ZERO));
    }
}
