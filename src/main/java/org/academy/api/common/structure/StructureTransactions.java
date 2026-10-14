package org.academy.api.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.structure.transaction.StructureCaptureCoordinator;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend-independent structure capture entry shared by skills, programs, and non-player
 * callers. A capture stages its writes: preflight, durable journal, target reservation,
 * verified copy, source removal, and published commit. Unavailable backends fail
 * explicitly and never fall back to a legacy entity; failures retain the source blocks
 * unless the journal proves a committed removal.
 */
public final class StructureTransactions {
    private StructureTransactions() {}

    /**
     * Starts a capture transaction in a live host level on its owning server thread.
     * The returned result is either a final rejection (nothing was written) or a
     * pending transaction id to poll with {@link #poll}.
     */
    public static StructureCaptureResult capture(ServerLevel host, StructureCaptureRequest request) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(request, "request");
        return StructureCaptureCoordinator.capture(host, request);
    }

    /** Returns the current result of a transaction, or empty for an unknown identity. */
    public static Optional<StructureCaptureResult> poll(MinecraftServer server, UUID transactionId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(transactionId, "transactionId");
        return StructureCaptureCoordinator.poll(server, transactionId);
    }

    /**
     * Removes a structure a capture created but that no controller adopted. Fallback skill
     * rejections use this so a failed adoption never leaves an orphan unit; false means the
     * backend could not remove it and the caller must report the retained structure.
     */
    public static boolean discard(MinecraftServer server, StructureRef reference) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reference, "reference");
        return StructureCaptureCoordinator.discard(server, reference);
    }
}
