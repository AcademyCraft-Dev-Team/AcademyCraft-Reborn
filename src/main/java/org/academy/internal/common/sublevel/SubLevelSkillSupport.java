package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;

import java.util.List;
import java.util.Optional;

/**
 * Stable server-side bridge for skills and programs that want plot-backed SubLevels instead of
 * the legacy per-region backend. It deliberately exposes no client or Sable types.
 */
public final class SubLevelSkillSupport {
    private SubLevelSkillSupport() {
    }

    /** Captures real host blocks into a new SubLevel; the source blocks are removed on success. */
    public static Optional<SubLevelId> capture(ServerLevel host, List<BlockPos> positions) {
        return capture(host, positions, (level, position) -> true);
    }

    /** Permission-aware skill capture; every source cell is checked before plot allocation. */
    public static Optional<SubLevelId> capture(ServerLevel host, List<BlockPos> positions,
                                               java.util.function.BiPredicate<ServerLevel, BlockPos> permission) {
        var runtime = host.getServer() == null ? null : SubLevelServer.runtime(host.getServer()).orElse(null);
        if (runtime == null) return Optional.empty();
        try {
            return Optional.of(SubLevelCaptureService.capture(host, runtime, positions, permission).id());
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.warn("SubLevel skill capture failed for {} cells", positions.size(), failure);
            return Optional.empty();
        }
    }

    public static Optional<StructurePose> pose(MinecraftServer server, SubLevelId id) {
        return SubLevelServer.runtime(server).flatMap(runtime -> runtime.get(id)).map(SubLevel::pose);
    }

    /** Translation-only skill relocation; preserves the plot and physics body ownership. */
    public static boolean translate(MinecraftServer server, SubLevelId id, Vec3 delta) {
        return SubLevelServer.runtime(server).map(runtime -> runtime.shift(id, delta)).orElse(false);
    }

    /** Yaw rotation around the local bounds center for skill/program authoring. */
    public static boolean rotate(MinecraftServer server, SubLevelId id, double yawDegrees) {
        return SubLevelServer.runtime(server).map(runtime -> runtime.rotate(id, yawDegrees)).orElse(false);
    }
}
