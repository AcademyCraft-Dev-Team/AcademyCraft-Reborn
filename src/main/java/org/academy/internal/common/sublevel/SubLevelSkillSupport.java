package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructurePose;
import org.jspecify.annotations.Nullable;

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
        var runtime = host.getServer() == null ? null : SubLevelServer.runtime(host).orElse(null);
        if (runtime == null) return Optional.empty();
        try {
            return Optional.of(SubLevelCaptureService.capture(host, runtime, positions, permission).id());
        } catch (RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.warn("SubLevel skill capture failed for {} cells", positions.size(), failure);
            return Optional.empty();
        }
    }

    public static Optional<StructurePose> pose(MinecraftServer server, SubLevelId id) {
        return SubLevelServer.runtime(server, id).flatMap(runtime -> runtime.get(id)).map(SubLevel::pose);
    }

    /** Translation-only skill relocation; preserves the plot and physics body ownership. */
    public static boolean translate(MinecraftServer server, SubLevelId id, Vec3 delta) {
        return SubLevelServer.runtime(server, id).map(runtime -> runtime.shift(id, delta)).orElse(false);
    }

    /** Restores plot content to the host and removes the SubLevel. */
    public static SubLevelRestoreService.Result staticize(MinecraftServer server, SubLevelId id,
                                                           net.minecraft.core.@Nullable BlockPos target) {
        return staticize(server, id, target, null, 0.0);
    }

    /** Actor-aware restore used by player skills and program nodes. */
    public static SubLevelRestoreService.Result staticize(MinecraftServer server, SubLevelId id,
                                                           net.minecraft.core.@Nullable BlockPos target,
                                                           net.minecraft.server.level.@Nullable ServerPlayer actor,
                                                           double maximumDistance) {
        var runtime = SubLevelServer.runtime(server, id).orElse(null);
        if (runtime == null) return new SubLevelRestoreService.Result(0, "unavailable");
        return SubLevelRestoreService.staticize(runtime.host(), runtime, id, target, actor, maximumDistance);
    }

    /** Starts a bounded continuous move toward a world target. */
    public static boolean moveTo(MinecraftServer server, SubLevelId id, Vec3 target, double seconds) {
        return SubLevelMotionController.moveTo(server, id, target, seconds);
    }

    /** Moves a specific selected surface anchor toward the world target. */
    public static boolean moveTo(MinecraftServer server, SubLevelId id, Vec3 localAnchor,
                                 Vec3 target, double seconds) {
        return SubLevelMotionController.moveTo(server, id, localAnchor, target, seconds);
    }

    /** Bounded constant-velocity thrust for direct-skill motion. */
    public static boolean thrust(MinecraftServer server, SubLevelId id, Vec3 direction, double speed,
                                 double seconds) {
        return SubLevelMotionController.thrust(server, id, direction, speed, seconds);
    }

    /** Bounded launch velocity replacing previous linear and angular movement. */
    public static boolean launch(MinecraftServer server, SubLevelId id, Vec3 direction, double speed) {
        return SubLevelServer.runtime(server, id).map(runtime -> runtime.launch(id, direction, speed)).orElse(false);
    }

    /** Yaw rotation around the local bounds center for skill/program authoring. */
    public static boolean rotate(MinecraftServer server, SubLevelId id, double yawDegrees) {
        return SubLevelServer.runtime(server, id).map(runtime -> runtime.rotate(id, yawDegrees)).orElse(false);
    }

    /** Sets all three rotation axes while preserving the current world mass center. */
    public static boolean rotateTo(MinecraftServer server, SubLevelId id, StructurePose target) {
        return SubLevelServer.runtime(server, id).map(runtime -> runtime.rotateTo(id, target)).orElse(false);
    }
}
