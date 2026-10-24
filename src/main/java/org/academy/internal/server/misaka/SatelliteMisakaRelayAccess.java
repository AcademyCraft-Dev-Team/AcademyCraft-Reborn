package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.misaka.MisakaRelayAccess;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;

import java.util.UUID;

/** Live relay-satellite access backed by {@link MisakaRelayRegistry}. */
public final class SatelliteMisakaRelayAccess implements MisakaRelayAccess {
    public static final SatelliteMisakaRelayAccess INSTANCE = new SatelliteMisakaRelayAccess();

    private SatelliteMisakaRelayAccess() {
    }

    @Override
    public boolean grantsAccess(ServerLevel level, BlockPos samplePos, UUID networkId) {
        if (level == null || networkId == null) {
            return false;
        }
        var server = level.getServer();
        if (server == null) {
            return false;
        }
        return MisakaRelayRegistry.get(server).hasActiveRelay(networkId, level.dimension());
    }
}
