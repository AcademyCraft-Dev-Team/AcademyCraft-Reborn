package org.academy.api.common.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Misaka network address translation / wireless topology façade.
 * Production impl is installed from server bootstrap; default is {@link NoopMisakaNAT}.
 */
public interface MisakaNAT {
    AtomicReference<@Nullable MisakaNAT> INSTALLED = new AtomicReference<>();
    AtomicReference<@Nullable MisakaNAT> TESTING_OVERRIDE = new AtomicReference<>();

    static MisakaNAT get() {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        var installed = INSTALLED.get();
        return installed != null ? installed : NoopMisakaNAT.INSTANCE;
    }

    /** Server bootstrap: install the production implementation. Pass null to clear. */
    static void install(@Nullable MisakaNAT nat) {
        INSTALLED.set(nat);
    }

    /** Test-only stub. Pass null to clear. */
    static void testingInstall(@Nullable MisakaNAT nat) {
        TESTING_OVERRIDE.set(nat);
    }

    List<String> listAvailableNodes(ServerLevel level, BlockPos near);

    Optional<BlockPos> findNode(ServerLevel level, String nodeName);

    BlockPos resolveNetworkId(ServerLevel level, BlockPos nodePos);

    boolean bindSisterToNode(ServerLevel level, UUID misakaUuid, BlockPos nodePos);

    boolean unbindSister(MinecraftServer server, UUID misakaUuid);

    boolean hasReconstructionWork(MinecraftServer server, BlockPos nodePos, @Nullable UUID except);

    int countNetworkSisters(ServerLevel level, BlockPos nodePos);

    /** Paginated misakaUuid list for the topology rooted at {@code nodePos}. */
    List<UUID> listNetworkSisters(ServerLevel level, BlockPos nodePos, int offset, int limit);

    /**
     * Whether {@code pos} can use Misaka network services for the topology rooted at {@code networkId}
     * (energy coverage or relay satellite).
     */
    boolean canUseMisakaService(ServerLevel level, BlockPos networkId, BlockPos pos);
}
