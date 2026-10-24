package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.academy.api.common.structure.upgrade.StructureOrientations;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in, copied-world fixture exercising the same public control API with either solver. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureOrientationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureOrientationSmoke");
    private static final StructureOrientation.Limits LIMITS = new StructureOrientation.Limits(
            2, 8, 10_000, 240, 0.01, 0.03, 0, 32_768);
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static volatile boolean ready, complete, failed, closing;
    private static volatile Path resultPath;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity;
    private static float oldYaw, oldPitch;
    private static boolean oldNoGravity;
    private static GameType oldMode;
    private static UUID transaction, operation;
    private static final UUID CONTROL = UUID.randomUUID();
    private static StructureRef reference;
    private static BlockPos seed;
    private static Vec3 hoverCenter, previousCenter, expectedSpinAxis;
    private static Vec3 gravity = new Vec3(0, -9.81, 0);
    private static StructurePose target;
    private static String scenario;
    private static int age, lastTick = -1, stage, stageAge;
    private static double greatestAngularSpeed, maximumCenterStep, maximumSpinAlignment;
    private static boolean releasePending, launchPending, queuedBrakeIssued, lifecyclePending;
    private static int launchTicks;
    private static Vec3 launchAngular;
    private static double previousBrakingSpeed;
    private static ArmorStand lifecycleActor;
    private static UUID lifecycleControl;
    private static long lifecycleRemovedAt;

    private StructureOrientationSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        try {
            check(++age < 1_800, "orientation fixture deadline");
            if (player == null) { begin(server); return; }
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (reference == null) { capture(server); return; }
            var motion = motion();
            var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (previousCenter != null) {
                double distance = center.distanceTo(previousCenter);
                maximumCenterStep = Math.max(maximumCenterStep, distance);
                check(distance < 0.4, "unexpected COM displacement in one server tick: " + distance);
            }
            previousCenter = center;
            if (releasePending) { checkReleased(); return; }
            if (launchPending) { checkLaunched(motion); return; }
            if (lifecyclePending) { checkActorCleanup(); return; }
            holdTranslation(motion);
            if (operation == null) { startStage(); return; }
            stageAge++;
            greatestAngularSpeed = Math.max(greatestAngularSpeed, motion.angularVelocity().length());
            if (expectedSpinAxis != null && motion.angularVelocity().length() > 0.2) {
                maximumSpinAlignment = Math.max(maximumSpinAlignment,
                        motion.angularVelocity().normalize().dot(expectedSpinAxis));
            }
            var receipt = StructureOrientations.service().status(context(),
                    new StructureOrientation.StatusRequest(CONTROL, operation));
            if (stage == 6 && queuedBrakeIssued) {
                double speed = motion.angularVelocity().length();
                check(previousBrakingSpeed - speed <= 4 * 0.05 + 0.025,
                        "queued brake exceeded its stricter angular acceleration budget");
                previousBrakingSpeed = speed;
            }
            if (stage == 6 && !queuedBrakeIssued && stageAge >= 4
                    && receipt.status() == StructureOperation.Status.RUNNING) {
                check(motion.angularVelocity().length() > 0.2, "queued brake case has no existing inertia");
                var replacement = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, new Vec3(1, 0, 0), StructureOrientation.AxisFrame.WORLD, 1.3,
                        60, StructureOrientation.StopMode.BRAKE, LIMITS));
                operation = replacement.operationId().orElseThrow();
                var stopLimits = new StructureOrientation.Limits(2, 4, 5_000, 220, 0.01, 0.03, 0, 32_768);
                var stop = StructureOrientations.service().stop(context(), new StructureOrientation.StopRequest(
                        CONTROL, operation, StructureOrientation.StopMode.BRAKE, stopLimits));
                check(stop.status() == StructureOperation.Status.RUNNING
                        && stop.value().orElseThrow().phase() == StructureOrientation.Phase.BRAKING,
                        "queued BRAKE completed without stopping existing inertia: " + stop);
                check(motion().angularVelocity().length() > 0.2, "queued BRAKE mutated angular velocity outside a substep");
                check(stop.value().orElseThrow().plan().limits().equals(stopLimits), "queued BRAKE ignored its stricter limits");
                previousBrakingSpeed = motion.angularVelocity().length();
                queuedBrakeIssued = true;
                stageAge = 0;
                return;
            }
            if (stage == 7 && stageAge >= 4 && receipt.status() == StructureOperation.Status.RUNNING) {
                check(motion.angularVelocity().length() > 0.2, "launch case never started its angular operation");
                check(StructureDirectControl.submit(player.level(), reference, player, CONTROL,
                        new StructurePhysicsControl.Force(motion.localCenterOfMass(), Vec3.ZERO)),
                        "could not replace fixture hover force before launch");
                check(org.academy.internal.common.structure.StructureBackendRouter.launch(player.level(), reference,
                        new Vec3(0, 1, 0), 1), "fixture launch was rejected");
                launchAngular = motion().angularVelocity();
                launchPending = true;
                launchTicks = 0;
                return;
            }
            if (stageAge % 40 == 0) {
                AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_PROGRESS backend={} scenario={} ticks={} status={} detail={} omega={} com={}",
                        reference.backend(), scenario, stageAge, receipt.status(), receipt.detail(), motion.angularVelocity(), center);
            }
            if (receipt.status() == StructureOperation.Status.RUNNING
                    || receipt.status() == StructureOperation.Status.TERRAIN_PENDING) {
                check(stageAge < 260, "operation stalled: " + receipt.detail());
                return;
            }
            finishStage(receipt, motion);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void begin(MinecraftServer server) {
        player = server.getPlayerList().getPlayers().getFirst();
        oldPosition = player.position();
        oldVelocity = player.getDeltaMovement();
        oldYaw = player.getYRot();
        oldPitch = player.getXRot();
        oldNoGravity = player.isNoGravity();
        oldMode = player.gameMode.getGameModeForPlayer();
        int height = player.level().getMaxY() - 48;
        seed = new BlockPos(player.blockPosition().getX() + 5, height, player.blockPosition().getZ());
        for (var pos : BlockPos.betweenClosed(seed.offset(-6, -6, -6), seed.offset(7, 7, 7))) {
            player.level().getChunkAt(pos);
            check(player.level().getBlockState(pos).isAir(), "orientation arena is not empty: " + pos);
        }
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);
        player.connection.teleport(seed.getX() - 5, height, seed.getZ(), oldYaw, oldPitch);
        var source = List.of(seed, seed.east(), seed.south());
        for (var pos : source) place(pos, Blocks.IRON_BLOCK.defaultBlockState());
        var capture = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                source, source.size(), 2, StructureCaptureRequest.ALLOW, true));
        check(capture.pending() || capture.succeeded(), "physical capture rejected: " + capture);
        transaction = capture.transactionId();
        if (capture.succeeded()) adopt(capture.structure().orElseThrow());
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_SMOKE_START seed={} transaction={}", seed, transaction);
    }

    private static void capture(MinecraftServer server) {
        var result = StructureTransactions.poll(server, transaction).orElseThrow();
        if (result.pending()) return;
        check(result.succeeded(), "physical capture failed: " + result);
        adopt(result.structure().orElseThrow());
    }

    private static void adopt(StructureRef captured) {
        reference = captured;
        if (!org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
            gravity = SableGravity.current();
        }
        check(StructureDirectControl.acquire(player.level(), reference, player, CONTROL)
                == StructureDirectControl.Status.ACTIVE, "could not acquire fresh structure");
        var motion = motion();
        hoverCenter = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        previousCenter = hoverCenter;
        holdTranslation(motion);
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_CAPTURED backend={} reference={} mass={} center={}",
                reference.backend(), reference, motion.mass(), hoverCenter);
    }

    private static void holdTranslation(StructureDynamics.State motion) {
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        var acceleration = hoverCenter.subtract(center).scale(16)
                .subtract(motion.linearVelocity().scale(8)).subtract(gravity);
        var force = acceleration.scale(motion.mass());
        check(force.length() <= StructurePhysicsControl.MAX_FORCE, "fixture hover force exceeds API budget");
        check(StructureDirectControl.submit(player.level(), reference, player, CONTROL,
                new StructurePhysicsControl.Force(motion.localCenterOfMass(), force)), "hover submission rejected");
        check(center.distanceTo(hoverCenter) < 1.0, "fixture cannot maintain COM suspension");
    }

    private static void startStage() {
        var before = motion();
        var center = before.pose().localToWorldPoint(before.localCenterOfMass());
        StructureOperation.Result<StructureOrientation.Snapshot> receipt;
        expectedSpinAxis = null;
        target = null;
        switch (stage) {
            case 0 -> {
                scenario = "arbitrary_axis_negative_rotate";
                target = OrientationTargets.axisAngle(new Vec3(1, 2, -1), -1.1);
                receipt = rotate(target);
            }
            case 1, 2 -> {
                scenario = stage == 1 ? "world_spin_brake" : "local_spin_brake";
                var axis = new Vec3(1, 0, 0);
                var frame = stage == 1 ? StructureOrientation.AxisFrame.WORLD : StructureOrientation.AxisFrame.LOCAL;
                expectedSpinAxis = (stage == 1 ? axis : before.pose().localToWorldDirection(axis)).scale(-1);
                receipt = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, axis, frame, -1.2, 30, StructureOrientation.StopMode.BRAKE, LIMITS));
            }
            case 3 -> {
                scenario = "nearest_grid_reset";
                receipt = StructureOrientations.service().reset(context(), new StructureOrientation.ResetRequest(
                        CONTROL, StructureOrientation.ResetMode.NEAREST_GRID_YAW,
                        StructureOrientation.MissingBaselinePolicy.REJECT, LIMITS));
                target = receipt.value().orElseThrow().plan().targetRotation().orElseThrow();
            }
            case 4 -> {
                scenario = "replacement_rejects_old_stop";
                var old = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1,
                        80, StructureOrientation.StopMode.BRAKE, LIMITS));
                var oldId = old.operationId().orElseThrow();
                target = StructurePose.identity(Vec3.ZERO);
                receipt = rotate(target);
                var replacement = receipt.operationId().orElseThrow();
                check(!oldId.equals(replacement), "replacement reused operation identity");
                var staleStop = StructureOrientations.service().stop(context(), new StructureOrientation.StopRequest(
                        CONTROL, oldId, StructureOrientation.StopMode.RELEASE_INERTIA, LIMITS));
                check(staleStop.operationId().filter(replacement::equals).isEmpty(), "old stop controlled replacement");
                var current = StructureOrientations.service().status(context(),
                        new StructureOrientation.StatusRequest(CONTROL, replacement));
                check(current.status() == StructureOperation.Status.RUNNING
                        || current.status() == StructureOperation.Status.COMPLETED, "old stop cancelled replacement");
            }
            case 5 -> {
                scenario = "wall_blocks_complete_rotation_arc";
                int wallX = (int) Math.round(before.pose().worldOrigin().x) + 2;
                int wallY = (int) Math.floor(center.y);
                int wallZ = (int) Math.floor(center.z);
                for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                    place(new BlockPos(wallX, wallY + y, wallZ + z), Blocks.BEDROCK.defaultBlockState());
                }
                target = OrientationTargets.axisAngle(new Vec3(0, 1, 0), Math.PI / 2);
                receipt = rotate(target);
            }
            case 6 -> {
                scenario = "queued_brake_stops_existing_inertia";
                for (var pos : PLACED) if (player.level().getBlockState(pos).is(Blocks.BEDROCK)) {
                    player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                receipt = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1.5,
                        80, StructureOrientation.StopMode.BRAKE, LIMITS));
            }
            case 7 -> {
                scenario = "launch_cancels_active_spin";
                receipt = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1.5,
                        80, StructureOrientation.StopMode.BRAKE, LIMITS));
            }
            case 8 -> {
                scenario = "lease_release_cancels_queued_operation";
                receipt = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                        CONTROL, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1.5,
                        80, StructureOrientation.StopMode.BRAKE, LIMITS));
                operation = receipt.operationId().orElseThrow();
                StructureDirectControl.release(reference, CONTROL);
                releasePending = true;
                return;
            }
            default -> throw new IllegalStateException("Unknown orientation stage " + stage);
        }
        check(receipt.status() == StructureOperation.Status.RUNNING
                || receipt.status() == StructureOperation.Status.COMPLETED
                || stage == 5 && receipt.status() == StructureOperation.Status.BLOCKED,
                "angular request rejected: " + receipt.status() + "/" + receipt.detail());
        operation = receipt.operationId().orElseThrow();
        var after = motion();
        check(center.distanceTo(after.pose().localToWorldPoint(after.localCenterOfMass())) < 1.0E-7,
                "submission teleported COM");
        check(OrientationTargets.rotationError(before.pose(), after.pose()).length() < 1.0E-7,
                "submission teleported orientation");
        stageAge = 0;
        greatestAngularSpeed = 0;
        maximumSpinAlignment = -1;
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_CASE_START backend={} scenario={} operation={}",
                reference.backend(), scenario, operation);
    }

    private static void finishStage(StructureOperation.Result<StructureOrientation.Snapshot> receipt,
                                    StructureDynamics.State actual) {
        check(stage != 7, "launch case ended before a real active angular command could be launched: " + receipt);
        check(stage != 6 || queuedBrakeIssued, "brake case ended before its queued stop request: " + receipt);
        var result = new LinkedHashMap<String, Object>();
        result.put("scenario", scenario);
        result.put("status", receipt.status().name());
        result.put("detail", receipt.detail());
        result.put("ticks", stageAge);
        result.put("max_angular_speed", greatestAngularSpeed);
        result.put("max_com_step", maximumCenterStep);
        if (stage == 5) {
            check(receipt.status() == StructureOperation.Status.BLOCKED, "wall did not block angular operation");
            double error = OrientationTargets.rotationError(actual.pose(), target).length();
            check(error > 0.5, "body rotated through the blocking wall");
            result.put("remaining_angle", error);
            for (var pos : PLACED) if (!pos.equals(seed) && !pos.equals(seed.east()) && !pos.equals(seed.south())) {
                check(player.level().getBlockState(pos).is(Blocks.BEDROCK), "angular operation broke its hard wall");
            }
        } else {
            check(receipt.status() == StructureOperation.Status.COMPLETED, "angular operation failed: " + receipt);
            check(actual.angularVelocity().length() <= LIMITS.angularSpeedTolerance() + 0.01,
                    "completion left excessive angular speed");
            if (target != null) {
                double error = OrientationTargets.rotationError(actual.pose(), target).length();
                check(error <= LIMITS.angleTolerance() + 0.003, "completion did not reach requested angle: " + error);
                result.put("actual_angle_error", error);
            }
            if (expectedSpinAxis != null) {
                check(greatestAngularSpeed > 0.5, "spin never produced physical angular velocity");
                check(maximumSpinAlignment > 0.95, "spin used the wrong axis/frame/sign: " + maximumSpinAlignment);
                check(stageAge >= 30, "finite spin completed before its physical duration");
                result.put("spin_axis_alignment", maximumSpinAlignment);
            }
        }
        result.put("pass", true);
        RESULTS.add(result);
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_CASE_PASSED backend={} result={}", reference.backend(), result);
        stage++;
        operation = null;
    }

    private static void checkLaunched(StructureDynamics.State actual) {
        var receipt = StructureOrientations.service().status(context(),
                new StructureOrientation.StatusRequest(CONTROL, operation));
        check(receipt.status() == StructureOperation.Status.COMPLETED
                && receipt.value().orElseThrow().phase() == StructureOrientation.Phase.STOPPED
                && receipt.detail().equals("orientation_launched"), "launch retained active angular control: " + receipt);
        check(StructureDirectControl.occupied(reference, context().runtimeId()), "launch unexpectedly revoked its standing lease");
        check(actual.angularVelocity().length() <= launchAngular.length() + 0.05,
                "cancelled spin accelerated the body after launch");
        if (++launchTicks < 3) return;
        RESULTS.add(Map.of("scenario", scenario, "status", receipt.status().name(), "post_launch_ticks", launchTicks,
                "post_launch_angular_speed", actual.angularVelocity().length(), "pass", true));
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_CASE_PASSED backend={} scenario={} post_launch_ticks={} omega={}",
                reference.backend(), scenario, launchTicks, actual.angularVelocity());
        hoverCenter = actual.pose().localToWorldPoint(actual.localCenterOfMass());
        launchPending = false;
        stage++;
        operation = null;
    }

    private static void checkReleased() {
        var receipt = StructureOrientations.service().status(context(),
                new StructureOrientation.StatusRequest(CONTROL, operation));
        check(receipt.status() != StructureOperation.Status.RUNNING
                && receipt.status() != StructureOperation.Status.TERRAIN_PENDING, "released lease still controls body");
        check(!StructureDirectControl.occupied(reference, context().runtimeId()), "release retained lease");
        var rejected = StructureOrientations.service().spin(context(), new StructureOrientation.SpinRequest(
                CONTROL, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1,
                20, StructureOrientation.StopMode.BRAKE, LIMITS));
        check(rejected.status() != StructureOperation.Status.RUNNING, "released token accepted new command");
        RESULTS.add(Map.of("scenario", scenario, "status", receipt.status().name(), "pass", true));
        releasePending = false;
        scenario = "removed_nonplayer_actor_releases_idle_lease";
        var center = motion().pose().localToWorldPoint(motion().localCenterOfMass());
        lifecycleActor = new ArmorStand(player.level(), center.x + 4, center.y, center.z);
        lifecycleActor.setNoGravity(true);
        check(player.level().addFreshEntity(lifecycleActor), "could not spawn lifecycle actor");
        lifecycleControl = UUID.randomUUID();
        check(StructureDirectControl.acquire(player.level(), reference, lifecycleActor, lifecycleControl)
                == StructureDirectControl.Status.ACTIVE, "non-player actor could not acquire structure");
        var actorContext = StructureOrientations.context(player.level(), reference, lifecycleActor).orElseThrow();
        var angular = StructureOrientations.service().spin(actorContext, new StructureOrientation.SpinRequest(
                lifecycleControl, new Vec3(0, 1, 0), StructureOrientation.AxisFrame.WORLD, 1,
                20, StructureOrientation.StopMode.RELEASE_INERTIA, LIMITS));
        check(angular.status() == StructureOperation.Status.RUNNING, "non-player angular command rejected");
        var stopped = StructureOrientations.service().stop(actorContext, new StructureOrientation.StopRequest(
                lifecycleControl, angular.operationId().orElseThrow(), StructureOrientation.StopMode.RELEASE_INERTIA, LIMITS));
        check(stopped.status() == StructureOperation.Status.COMPLETED, "non-player angular cancellation failed");
        check(!StructureOrientations.controls(player.level(), reference, lifecycleControl), "lifecycle fixture retained active angle work");
        check(StructureDirectControl.occupied(reference, actorContext.runtimeId()), "cancelling angle work incorrectly released lease");
        lifecycleActor.discard();
        lifecycleRemovedAt = player.level().getGameTime();
        lifecyclePending = true;
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_CASE_START backend={} scenario={} actor={} inactive_angular_registry=true",
                reference.backend(), scenario, lifecycleActor.getUUID());
    }

    private static void checkActorCleanup() {
        check(player.level().getGameTime() > lifecycleRemovedAt, "lifecycle cleanup was checked before a real server tick");
        check(lifecycleActor.isRemoved(), "fixture actor is still present");
        check(!StructureDirectControl.occupied(reference, context().runtimeId()),
                "removed non-player actor left an idle standing lease");
        var replacement = UUID.randomUUID();
        check(StructureDirectControl.acquire(player.level(), reference, player, replacement)
                == StructureDirectControl.Status.ACTIVE, "player could not acquire after automatic actor cleanup");
        StructureDirectControl.release(reference, replacement);
        RESULTS.add(Map.of("scenario", scenario, "automatic_tick_cleanup", true,
                "waited_ticks", player.level().getGameTime() - lifecycleRemovedAt, "pass", true));
        cleanup();
        writeResult(true, "");
        complete = true;
        AcademyCraft.LOGGER.info("STRUCTURE_ORIENTATION_SMOKE_PASSED backend={} cases={} results={}",
                reference.backend(), RESULTS.size(), RESULTS);
    }

    private static StructureOperation.Result<StructureOrientation.Snapshot> rotate(StructurePose rotation) {
        return StructureOrientations.service().rotateTo(context(), new StructureOrientation.RotateRequest(CONTROL, rotation, LIMITS));
    }

    private static StructureOperation.Context context() {
        return StructureOrientations.context(player.level(), reference, player).orElseThrow();
    }

    private static StructureDynamics.State motion() {
        return StructureDynamics.motion(player.level(), reference).orElseThrow();
    }

    private static void place(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        check(player.level().getBlockState(position).isAir(), "fixture placement overwrites content: " + position);
        check(player.level().setBlock(position, state, 3), "fixture placement failed: " + position);
        PLACED.add(position.immutable());
    }

    private static void cleanup() {
        if (player == null) return;
        try {
            if (lifecycleActor != null) lifecycleActor.discard();
            if (reference != null && lifecycleControl != null) StructureDirectControl.release(reference, lifecycleControl);
            if (reference != null) {
                StructureOrientations.cancel(player.level(), reference, CONTROL);
                StructureDirectControl.release(reference, CONTROL);
                check(StructureTransactions.discard(player.level().getServer(), reference), "fixture structure cleanup failed");
            }
            for (var pos : PLACED) player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } finally {
            player.resetFallDistance();
            player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
            player.setDeltaMovement(oldVelocity);
            player.setNoGravity(oldNoGravity);
            player.setGameMode(oldMode);
        }
    }

    private static void writeResult(boolean passed, String failure) {
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed);
            report.put("backend", reference == null ? "capture_pending" : reference.backend().toString());
            report.put("cases", RESULTS);
            report.put("failure", failure);
            Files.writeString(resultPath, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failureToWrite) {
            AcademyCraft.LOGGER.error("Could not write orientation smoke report", failureToWrite);
        }
    }

    private static void fail(Throwable failure) {
        if (complete || failed) return;
        failed = true;
        AcademyCraft.LOGGER.error("STRUCTURE_ORIENTATION_SMOKE_FAILED scenario={} stage={} age={}", scenario, stage, age, failure);
        try { cleanup(); }
        catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
        writeResult(false, failure.toString());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (failed || complete) {
            closing = true;
            if (minecraft.player != null) minecraft.player.resetFallDistance();
            minecraft.stop();
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) return;
        var directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("orientation smoke requires an isolated native-sublevel-direct-smoke* directory"));
            return;
        }
        resultPath = directory.resolve("structure-orientation-result.json");
        minecraft.player.setNoGravity(true);
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.resetFallDistance();
        ready = true;
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    /** Keep optional backend linkage out of the no-Sable fixture's class initialization. */
    private static final class SableGravity {
        private static Vec3 current() {
            var gravity = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(player.level());
            return new Vec3(gravity.x, gravity.y, gravity.z);
        }
    }
}
