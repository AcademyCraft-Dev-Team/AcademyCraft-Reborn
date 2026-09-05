package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.server.misaka.WirelessForwardingMisakaNAT;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MisakaNAT {
    static MisakaNAT get() {
        return WirelessForwardingMisakaNAT.INSTANCE;
    }

    List<String> listAvailableNodes(ServerLevel level, BlockPos near);

    Optional<BlockPos> findNode(ServerLevel level, String nodeName);

    BlockPos resolveNetworkId(ServerLevel level, BlockPos nodePos);

    boolean bindSisterToNode(ServerLevel level, UUID misakaUuid, BlockPos nodePos);

    boolean unbindSister(MinecraftServer server, UUID misakaUuid);

    boolean hasReconstructionWork(MinecraftServer server, BlockPos nodePos, @Nullable UUID except);
}
