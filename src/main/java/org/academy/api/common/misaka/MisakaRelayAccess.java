package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Future relay-satellite access for Misaka network services outside energy coverage.
 * Current implementation never grants access.
 */
public interface MisakaRelayAccess {
    static MisakaRelayAccess get() {
        return NoopMisakaRelayAccess.INSTANCE;
    }

    boolean grantsAccess(ServerLevel level, BlockPos samplePos, BlockPos networkId);
}
