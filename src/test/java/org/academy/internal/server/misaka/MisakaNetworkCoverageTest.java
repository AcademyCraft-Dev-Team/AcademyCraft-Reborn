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
    void clearCache() {
        MisakaNetworkCoverage.invalidate();
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
        // Simulates two topology components: only network-A spheres should match.
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
    void relayNoopNeverGrantsAccess() {
        assertFalse(MisakaRelayAccess.get().grantsAccess(null, BlockPos.ZERO, BlockPos.ZERO));
    }
}
