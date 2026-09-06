package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Relay-satellite access for Misaka network services outside energy coverage.
 * Production impl is installed from server bootstrap; default is {@link NoopMisakaRelayAccess}.
 */
public interface MisakaRelayAccess {
    AtomicReference<@Nullable MisakaRelayAccess> INSTALLED = new AtomicReference<>();
    AtomicReference<@Nullable MisakaRelayAccess> TESTING_OVERRIDE = new AtomicReference<>();

    static MisakaRelayAccess get() {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        var installed = INSTALLED.get();
        return installed != null ? installed : NoopMisakaRelayAccess.INSTANCE;
    }

    /** Server bootstrap: install the production implementation. Pass null to clear. */
    static void install(@Nullable MisakaRelayAccess access) {
        INSTALLED.set(access);
    }

    /** Test-only: install a stub access implementation. Pass null to clear. */
    static void testingInstall(@Nullable MisakaRelayAccess access) {
        TESTING_OVERRIDE.set(access);
    }

    boolean grantsAccess(ServerLevel level, BlockPos samplePos, BlockPos networkId);
}
