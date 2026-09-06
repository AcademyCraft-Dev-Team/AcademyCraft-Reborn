package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.server.misaka.SatelliteMisakaRelayAccess;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Relay-satellite access for Misaka network services outside energy coverage.
 */
public interface MisakaRelayAccess {
    AtomicReference<@Nullable MisakaRelayAccess> TESTING_OVERRIDE = new AtomicReference<>();

    static MisakaRelayAccess get() {
        var override = TESTING_OVERRIDE.get();
        return override != null ? override : SatelliteMisakaRelayAccess.INSTANCE;
    }

    /** Test-only: install a stub access implementation. Pass null to clear. */
    static void testingInstall(@Nullable MisakaRelayAccess access) {
        TESTING_OVERRIDE.set(access);
    }

    boolean grantsAccess(ServerLevel level, BlockPos samplePos, BlockPos networkId);
}
