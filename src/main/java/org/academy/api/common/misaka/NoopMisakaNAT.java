package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stub NAT used before server install and as a safe default. */
public final class NoopMisakaNAT implements MisakaNAT {
    public static final NoopMisakaNAT INSTANCE = new NoopMisakaNAT();

    private NoopMisakaNAT() {
    }

    @Override
    public List<String> listAvailableNodes(ServerLevel level, BlockPos near) {
        return List.of();
    }

    @Override
    public Optional<BlockPos> findNode(ServerLevel level, String nodeName) {
        return Optional.empty();
    }

    @Override
    public UUID resolveNetworkId(ServerLevel level, BlockPos nodePos) {
        // Deterministic stand-in so equality checks work without a live registry.
        return new UUID(0L, nodePos == null ? 0L : nodePos.asLong());
    }

    @Override
    public boolean bindSisterToNode(ServerLevel level, UUID misakaUuid, BlockPos nodePos) {
        return false;
    }

    @Override
    public boolean unbindSister(MinecraftServer server, UUID misakaUuid) {
        return false;
    }

    @Override
    public boolean hasReconstructionWork(MinecraftServer server, BlockPos nodePos, @Nullable UUID except) {
        return false;
    }

    @Override
    public int countNetworkSisters(ServerLevel level, BlockPos nodePos) {
        return 0;
    }

    @Override
    public List<UUID> listNetworkSisters(ServerLevel level, BlockPos nodePos, int offset, int limit) {
        return List.of();
    }

    @Override
    public boolean canUseMisakaService(ServerLevel level, UUID networkId, BlockPos pos) {
        return false;
    }
}
