package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Stub: never grants relay access. Kept for tests and fallback comparisons. */
public final class NoopMisakaRelayAccess implements MisakaRelayAccess {
    public static final NoopMisakaRelayAccess INSTANCE = new NoopMisakaRelayAccess();

    private NoopMisakaRelayAccess() {
    }

    @Override
    public boolean grantsAccess(ServerLevel level, BlockPos samplePos, UUID networkId) {
        return false;
    }
}
