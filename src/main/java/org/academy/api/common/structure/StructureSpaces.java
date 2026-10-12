package org.academy.api.common.structure;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.structure.NativeStructureRuntime;

import java.util.Objects;

/** Entry point for the server's independent structure-space backend. */
public final class StructureSpaces {
    private StructureSpaces() {
    }

    /** Returns the backend owned by this server; no player or entity is required. */
    public static StructureBackend backend(MinecraftServer server) {
        return NativeStructureRuntime.backend(Objects.requireNonNull(server, "server"));
    }

    /** Reports the independent backend's last presentation state without loading the structure. */
    public static StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference) {
        return NativeStructureRuntime.backend(Objects.requireNonNull(server, "server"))
                .presentationStatus(Objects.requireNonNull(reference, "reference"));
    }
}
