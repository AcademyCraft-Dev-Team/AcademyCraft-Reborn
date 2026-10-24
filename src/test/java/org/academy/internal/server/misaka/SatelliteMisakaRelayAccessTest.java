package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import org.academy.api.common.misaka.MisakaRelayAccess;
import org.academy.api.common.misaka.NoopMisakaRelayAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class SatelliteMisakaRelayAccessTest {
    @AfterEach
    void clear() {
        MisakaRelayAccess.testingInstall(null);
        MisakaRelayAccess.install(null);
    }

    @Test
    void installMakesSatelliteImplDefault() {
        MisakaRelayAccess.install(SatelliteMisakaRelayAccess.INSTANCE);
        assertSame(SatelliteMisakaRelayAccess.INSTANCE, MisakaRelayAccess.get());
    }

    @Test
    void grantsAccessDeniesWithoutLevel() {
        MisakaRelayAccess.install(SatelliteMisakaRelayAccess.INSTANCE);
        assertFalse(MisakaRelayAccess.get().grantsAccess(null, BlockPos.ZERO, UUID.randomUUID()));
    }

    @Test
    void noopDenies() {
        assertFalse(NoopMisakaRelayAccess.INSTANCE.grantsAccess(null, BlockPos.ZERO, UUID.randomUUID()));
    }
}
