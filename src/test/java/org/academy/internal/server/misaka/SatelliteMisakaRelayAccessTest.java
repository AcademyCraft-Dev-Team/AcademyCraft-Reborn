package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import org.academy.api.common.misaka.MisakaRelayAccess;
import org.academy.api.common.misaka.NoopMisakaRelayAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatelliteMisakaRelayAccessTest {
    @AfterEach
    void clear() {
        MisakaNetworkCoverage.invalidate();
        MisakaRelayAccess.testingInstall(null);
        MisakaRelayAccess.install(null);
    }

    @Test
    void defaultIsNoopUntilInstalled() {
        assertSame(NoopMisakaRelayAccess.INSTANCE, MisakaRelayAccess.get());
        MisakaRelayAccess.install(SatelliteMisakaRelayAccess.INSTANCE);
        assertSame(SatelliteMisakaRelayAccess.INSTANCE, MisakaRelayAccess.get());
    }

    @Test
    void nullLevelNeverGrants() {
        MisakaRelayAccess.install(SatelliteMisakaRelayAccess.INSTANCE);
        assertFalse(MisakaRelayAccess.get().grantsAccess(null, BlockPos.ZERO, BlockPos.ZERO));
    }

    @Test
    void noopStubStillDenies() {
        assertFalse(NoopMisakaRelayAccess.INSTANCE.grantsAccess(null, BlockPos.ZERO, new BlockPos(1, 2, 3)));
    }

    @Test
    void sphereMathStillIndependentOfRelay() {
        var sphere = new MisakaNetworkCoverage.Sphere(new BlockPos(0, 64, 0), 100.0);
        assertTrue(sphere.contains(new BlockPos(0, 64, 0)));
        assertFalse(sphere.contains(new BlockPos(50, 64, 0)));
    }
}
