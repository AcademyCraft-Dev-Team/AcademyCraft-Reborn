package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.control.LiveStructureExtraction;

import java.util.Optional;

/**
 * Shared capture-preview and binding entry point for skills, programs and non-player actors.
 * A preview is display-only; binding consumes server-owned state and never grants a capture,
 * a lease or a body movement by itself.
 */
public final class StructureExtractions {
    private StructureExtractions() {
    }

    /** The live service; extraction motion still reports UNSUPPORTED until it is wired. */
    public static StructureExtraction service() {
        return LiveStructureExtraction.INSTANCE;
    }

    /**
     * Resolves the runtime-only authority for an already loaded structure. The content revision
     * comes from the same backend observation the angular service uses, so a caller cannot
     * invent one; no structure is loaded and no lease is acquired.
     */
    public static Optional<StructureOperation.Context> context(ServerLevel host, StructureRef reference, Entity actor) {
        return StructureOrientations.context(host, reference, actor);
    }
}
