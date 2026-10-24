package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

/** Read-only gate for restoring marked sources; unrelated Sable structures are never modified. */
public final class SableStructureJoints {
    private SableStructureJoints() {}

    /** True also means that the active backend cannot prove absence of native constraints. */
    public static boolean hasAny(ServerLevel host, ServerSubLevel source) {
        if (!host.getServer().isSameThread() || source.getLevel() != host || source.isRemoved()) return true;
        try {
            var container = SubLevelContainer.getContainer(host);
            if (container == null) return true;
            var pipeline = container.physicsSystem().getPipeline();
            return !(SableReflection.unwrap(pipeline) instanceof TrackedPipeline tracked) || tracked.academy$hasJoint(SableReflection.unwrap(source));
        } catch (RuntimeException | LinkageError unavailable) {
            return true;
        }
    }

    /** Implemented only by the fingerprint-gated audited Rapier pipeline mixin. */
    public interface TrackedPipeline {
        boolean academy$hasJoint(Object source);
    }
}
