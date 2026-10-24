package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation;

import java.util.Optional;
import java.util.UUID;

/** Shared angular control entry point for skills, programs and non-player actors with a lease. */
public final class StructureOrientations {
    private StructureOrientations() {}

    public static StructureOrientation service() { return LiveStructureOrientation.INSTANCE; }

    /** Resolve the real backend content revision; never load a structure or acquire its lease. */
    public static Optional<StructureOperation.Context> context(ServerLevel host, StructureRef reference, Entity actor) {
        return LiveStructureOrientation.context(host, reference, actor);
    }

    /** Cancel only this token's angular command; translation, lease and physical inertia remain. */
    public static void cancel(ServerLevel host, StructureRef reference, UUID controlId) {
        LiveStructureOrientation.cancel(host, reference, controlId);
    }

    /** True only while this exact token has a pending/running angular operation in this host. */
    public static boolean controls(ServerLevel host, StructureRef reference, UUID controlId) {
        return LiveStructureOrientation.controls(host, reference, controlId);
    }
}
