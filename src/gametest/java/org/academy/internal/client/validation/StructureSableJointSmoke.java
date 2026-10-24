package org.academy.internal.client.validation;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.sable.SableStructureJoints;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Linked only in the opt-in Sable joint mode. Uses a real solver constraint, never a fake gate. */
final class StructureSableJointSmoke {
    private static UUID companionCapture, companionRuntime, markedCreation;
    private static StructureRef companion;
    private static BlockPos companionSeed;
    private static ServerSubLevel source;
    private static PhysicsConstraintHandle constraint;
    private static int retainedChecks;
    private static boolean removed;

    private StructureSableJointSmoke() {}

    static void beginCompanion(ServerPlayer player, BlockPos markedSeed) {
        check(SubLevelContainer.getContainer(player.level()) != null, "joint fixture requires Sable");
        companionSeed = markedSeed.offset(2, 0, 0);
        check(player.level().getBlockState(companionSeed).isAir(), "unmarked companion location occupied");
        check(player.level().setBlock(companionSeed, Blocks.GOLD_BLOCK.defaultBlockState(), 3), "companion seed rejected");
        var result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                List.of(companionSeed), 8, 8, StructureCaptureRequest.ALLOW, true));
        check(result.pending() || result.succeeded(), "unmarked companion capture failed: " + result);
        companionCapture = result.transactionId();
    }

    static boolean companionReady(ServerPlayer player) {
        if (companion == null) {
            var result = StructureTransactions.poll(player.level().getServer(), companionCapture).orElseThrow();
            if (result.pending()) return false;
            check(result.succeeded(), "unmarked companion capture failed: " + result);
            companion = result.structure().orElseThrow();
        }
        var handle = StructureSpaces.resolve(player.level().getServer(), companion).orElse(null);
        if (handle == null || StructureDynamics.motion(player.level(), companion).isEmpty()) return false;
        if (companionRuntime == null) companionRuntime = handle.runtimeId();
        assertCompanion(player);
        return true;
    }

    static void attachToWorld(ServerPlayer player, StructureRef marked) {
        assertCompanion(player);
        var container = SubLevelContainer.getContainer(player.level());
        source = (ServerSubLevel) container.getSubLevel(marked.id());
        check(source != null && !SableStructureJoints.hasAny(player.level(), source), "source already has a joint or unsupported pipeline");
        var localAnchor = new Vector3d(source.getMassTracker().getCenterOfMass());
        var worldAnchor = source.logicalPose().transformPosition(new Vector3d(localAnchor));
        // The second body is the world, so its frame expressed in the first body is inverse(A).
        var relativeRotation = new Quaterniond(source.logicalPose().orientation()).conjugate();
        constraint = container.physicsSystem().getPipeline().addConstraint(source, null,
                new FixedConstraintConfiguration(localAnchor, worldAnchor, relativeRotation));
        check(constraint != null && constraint.isValid(), "real fixed constraint creation failed");
        check(SableStructureJoints.hasAny(player.level(), source), "installed joint was not observed by the live gate");
        markedCreation = LiveStructureProvenance.metadata(player.level().getServer(), marked).orElseThrow().creationOperationId();
    }

    static void assertRetained(ServerPlayer player, StructureRef marked) {
        check(constraint != null && constraint.isValid(), "joint disappeared before the retention check");
        check(SableStructureJoints.hasAny(player.level(), source), "active joint gate was lost");
        check(SubLevelContainer.getContainer(player.level()).getSubLevel(marked.id()) == source,
                "automatic restoration replaced or removed the jointed source");
        check(StructureSpaces.resolve(player.level().getServer(), marked).orElseThrow().world()
                .read(BlockPos.ZERO).state().is(Blocks.IRON_BLOCK), "joint refusal changed source contents");
        check(LiveStructureProvenance.metadata(player.level().getServer(), marked).orElseThrow()
                .creationOperationId().equals(markedCreation), "joint refusal changed source provenance");
        assertCompanion(player);
        retainedChecks++;
    }

    static void removeJoint(ServerPlayer player) {
        check(constraint != null && constraint.isValid(), "joint is not live before explicit removal");
        constraint.remove();
        check(!constraint.isValid(), "constraint removal did not invalidate the solver handle");
        check(!SableStructureJoints.hasAny(player.level(), source), "removed joint still blocks restoration");
        removed = true;
    }

    static void assertCompanion(ServerPlayer player) {
        var handle = StructureSpaces.resolve(player.level().getServer(), companion).orElseThrow();
        check(handle.runtimeId().equals(companionRuntime), "unmarked companion activation was replaced");
        check(handle.world().read(BlockPos.ZERO).state().is(Blocks.GOLD_BLOCK), "unmarked companion content changed");
        check(LiveStructureProvenance.healthy(player.level().getServer())
                && LiveStructureProvenance.metadata(player.level().getServer(), companion).isEmpty(),
                "unmarked companion was retroactively marked");
        check(player.level().getBlockState(companionSeed).isAir(), "unmarked companion was restored as world content");
    }

    static Map<String, Object> result(ServerPlayer player) {
        assertCompanion(player);
        check(removed && retainedChecks >= 180, "joint refusal/release was not fully exercised");
        return Map.of("real_world_fixed_joint", true, "joint_retention_checks", retainedChecks,
                "joint_removed_before_retry", true, "unmarked_companion_uuid", companion.id().toString(),
                "unmarked_companion_runtime", companionRuntime.toString(), "unmarked_companion_preserved", true);
    }

    static void cleanup(ServerPlayer player) {
        if (constraint != null && constraint.isValid()) constraint.remove();
        if (companion != null && StructureSpaces.resolve(player.level().getServer(), companion).isPresent()) {
            check(StructureTransactions.discard(player.level().getServer(), companion), "unmarked companion cleanup failed");
        }
        if (companionSeed != null && player.level().getBlockState(companionSeed).is(Blocks.GOLD_BLOCK)) {
            player.level().setBlock(companionSeed, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
