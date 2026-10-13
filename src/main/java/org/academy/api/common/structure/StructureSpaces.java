package org.academy.api.common.structure;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.Objects;

/** Entry point for the server's selected structure-space backend. */
public final class StructureSpaces {
    private StructureSpaces() {
    }

    /** Returns the backend owned by this server; no player or entity is required. */
    public static StructureBackend backend(MinecraftServer server) {
        return StructureBackendRouter.backend(Objects.requireNonNull(server, "server"));
    }

    /** Reports compatibility and retained native content without migrating or activating it. */
    public static StructureBackendStatus status(MinecraftServer server) {
        return StructureBackendRouter.status(Objects.requireNonNull(server, "server"));
    }

    /** Resolves an exact loaded identity in the selected backend without loading content. */
    public static java.util.Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference) {
        return StructureBackendRouter.resolve(Objects.requireNonNull(server, "server"),
                Objects.requireNonNull(reference, "reference"));
    }

    /** Reports presentation without loading the structure. Sable owns its own render/network path. */
    public static StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference) {
        return StructureBackendRouter.presentationStatus(Objects.requireNonNull(server, "server"),
                Objects.requireNonNull(reference, "reference"));
    }
}
