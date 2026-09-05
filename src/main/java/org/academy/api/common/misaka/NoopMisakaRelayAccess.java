package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Stub: no relay satellites yet. */
final class NoopMisakaRelayAccess implements MisakaRelayAccess {
    static final NoopMisakaRelayAccess INSTANCE = new NoopMisakaRelayAccess();

    private NoopMisakaRelayAccess() {
    }

    @Override
    public boolean grantsAccess(ServerLevel level, BlockPos samplePos, BlockPos networkId) {
        return false;
    }
}
