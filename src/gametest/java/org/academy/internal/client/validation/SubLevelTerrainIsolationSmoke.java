package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.sublevel.SubLevel;
import org.academy.internal.common.sublevel.SubLevelActivationRegistry;
import org.academy.internal.common.sublevel.SubLevelDirectControl;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelMotionController;
import org.academy.internal.common.sublevel.SubLevelPhysicsUniverse;
import org.academy.internal.common.sublevel.SubLevelRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;

import java.util.Comparator;

/**
 * Runs exclusively against an isolated copy of a saved scene. A body lacking terrain coverage
 * must stay immobile and refuse controllers while a nearby admitted body moves and rotates for
 * ordinary server ticks. It neither rebuilds terrain nor calls the runtime/solver tick manually.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelTerrainIsolationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelTerrainIsolationSmoke");
    private static final int OBSERVATION_TICKS = 80;
    private static volatile boolean clientReady;
    private static volatile boolean closing;
    private static volatile boolean finished;
    private static boolean failed;
    private static int age;
    private static int lastTick = -1;
    private static int observed;
    private static ServerPlayer player;
    private static SubLevelRuntime runtime;
    private static SubLevelId pausedId;
    private static SubLevelId movingId;
    private static PhysicsPose pausedPose;
    private static StructurePose pausedPublishedPose;
    private static SubLevelPhysicsUniverse.Motion movingStart;
    private static Vec3 movingCenter;
    private static boolean previousNoGravity;
    private static Vec3 previousPlayerVelocity;
    private static double maximumDistance;
    private static double maximumRise;
    private static double maximumAngle;

    private SubLevelTerrainIsolationSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || finished || lastTick == event.getServer().getTickCount()) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        lastTick = server.getTickCount();
        try {
            if (player == null) {
                player = server.getPlayerList().getPlayers().getFirst();
                previousNoGravity = player.isNoGravity();
                previousPlayerVelocity = player.getDeltaMovement();
                AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_ISOLATION_START player={} dimension={}",
                        player.position(), player.level().dimension().identifier());
            }
            age++;
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            if (age > 600) throw new IllegalStateException("Terrain isolation smoke deadline");
            runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            if (movingId == null) {
                // Restoration, chunk tracking and the native terrain admission pass must run first.
                if (age < 100) return;
                if (!chooseBodies(server)) {
                    if (age % 20 == 0) logProgress("waiting");
                    if (age > 300) throw new IllegalStateException("Saved scene has no paused/nearby active pair");
                    return;
                }
                return;
            }
            observe(server);
        } catch (Throwable failure) {
            failed = true;
            finished = true;
            AcademyCraft.LOGGER.error("SUBLEVEL_TERRAIN_ISOLATION_FAILED age={} observed={} paused={} moving={} "
                            + "max_distance={} max_rise={} max_angle={}", age, observed, pausedId, movingId,
                    maximumDistance, maximumRise, maximumAngle, failure);
            restore();
        }
    }

    private static boolean chooseBodies(MinecraftServer server) {
        var universe = runtime.universe();
        var paused = runtime.subLevels().stream()
                .filter(body -> universe.isSimulationPaused(body.id()))
                .max(Comparator.comparingDouble(body -> body.worldCenter().distanceToSqr(player.position())))
                .orElse(null);
        var moving = runtime.subLevels().stream()
                .filter(body -> !universe.isSimulationPaused(body.id()) && universe.motion(body.id()).isPresent())
                .filter(body -> body.worldCenter().distanceToSqr(player.position()) < 96 * 96)
                .filter(body -> body.worldCenter().distanceToSqr(player.position()) > 5 * 5)
                .sorted(Comparator.comparingDouble(body -> body.worldCenter().distanceToSqr(player.position())))
                .filter(SubLevelTerrainIsolationSmoke::hasClearAscent)
                .findFirst()
                .orElse(null);
        if (paused == null || moving == null) return false;
        pausedId = paused.id();
        movingId = moving.id();
        pausedPose = universe.motion(pausedId).orElseThrow().pose();
        pausedPublishedPose = paused.pose();
        movingStart = universe.motion(movingId).orElseThrow();
        movingCenter = center(movingStart);
        rejectPausedControls(server, paused);
        SubLevelMotionController.cancel(movingId);
        if (!universe.setVelocity(movingId, new PhysicsVector(0, 8, 0), new PhysicsVector(0, 1, 0))) {
            throw new IllegalStateException("Covered body refused velocity");
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_ISOLATION_SELECTED paused={} paused_pose={} moving={} "
                        + "moving_pose={} moving_com={} total={} terrain={}", pausedId, pausedPose, movingId,
                movingStart.pose(), movingCenter, runtime.size(), runtime.terrainDiagnostics());
        return true;
    }

    private static boolean hasClearAscent(SubLevel body) {
        var entry = runtime.collisionIndex().entry(body.id()).orElse(null);
        if (entry == null || entry.localBoxes().isEmpty()) return false;
        var bounds = body.worldBounds().expandTowards(0, 1, 0);
        if (bounds.getXsize() * bounds.getYsize() * bounds.getZsize() > 32_768) return false;
        var boxes = new java.util.ArrayList<BlockStructureOrientedBox>();
        for (double lift : new double[]{0, 0.5, 1}) {
            var pose = new StructurePose(body.pose().worldOrigin().add(0, lift, 0), body.pose().rotation());
            for (var box : entry.localBoxes()) {
                // Solver contact slop is permitted; a body buried in surviving terrain is not.
                boxes.add(BlockStructureOrientedBox.transform(box.deflate(0.005), pose));
            }
        }
        for (var position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!player.level().isLoaded(position)) return false;
            var shape = player.level().getBlockState(position).getCollisionShape(player.level(), position);
            for (var local : shape.toAabbs()) {
                var terrain = BlockStructureOrientedBox.axisAligned(local.move(position));
                if (boxes.stream().anyMatch(box -> box.intersects(terrain))) return false;
            }
        }
        return true;
    }

    private static void rejectPausedControls(MinecraftServer server, SubLevel paused) {
        var generation = SubLevelActivationRegistry.activation(pausedId).generation();
        var reference = SubLevelStructureRefs.of(paused, player.level().dimension().identifier(), generation);
        if (SubLevelDirectControl.probe(player.level(), reference).state()
                != StructureDirectControl.Probe.State.UNAVAILABLE) {
            throw new IllegalStateException("Paused body remained available to direct controls");
        }
        var snapshot = StructureDynamics.motion(player.level(), reference).orElseThrow();
        if (!snapshot.pose().equals(pausedPublishedPose)) {
            throw new IllegalStateException("Paused body's read-only motion snapshot changed pose");
        }
        if (snapshot.linearVelocity().lengthSqr() != 0 || snapshot.angularVelocity().lengthSqr() != 0) {
            throw new IllegalStateException("Paused body's public motion predicted movement");
        }
        if (SubLevelMotionController.moveTo(server, pausedId, paused.worldCenter().add(0, 3, 0), 1)
                || SubLevelMotionController.thrust(server, pausedId, new Vec3(0, 1, 0), 3, 1)) {
            SubLevelMotionController.cancel(pausedId);
            throw new IllegalStateException("Paused body accepted move/thrust");
        }
        if (runtime.universe().setVelocity(pausedId, new PhysicsVector(0, 8, 0), PhysicsVector.ZERO)
                || runtime.universe().impulse(pausedId, new PhysicsVector(0, 10, 0), pausedPose.position())) {
            throw new IllegalStateException("Paused body accepted a physical velocity/impulse");
        }
    }

    private static void observe(MinecraftServer server) {
        var universe = runtime.universe();
        var paused = runtime.get(pausedId).orElseThrow();
        if (!universe.isSimulationPaused(pausedId)) {
            throw new IllegalStateException("The selected paused body lost its missing-coverage condition");
        }
        if (!pausedPose.equals(universe.motion(pausedId).orElseThrow().pose())
                || !pausedPublishedPose.equals(paused.pose())) {
            throw new IllegalStateException("Paused body moved while another body simulated");
        }
        rejectPausedControls(server, paused);
        if (universe.isSimulationPaused(movingId)) {
            throw new IllegalStateException("Nearby admitted body became terrain-paused");
        }
        var moving = universe.motion(movingId).orElseThrow();
        var currentCenter = center(moving);
        maximumDistance = Math.max(maximumDistance, currentCenter.distanceTo(movingCenter));
        maximumRise = Math.max(maximumRise, currentCenter.y - movingCenter.y);
        var initialRotation = movingStart.pose().rotation();
        var currentRotation = moving.pose().rotation();
        double dot = initialRotation.x() * currentRotation.x() + initialRotation.y() * currentRotation.y()
                + initialRotation.z() * currentRotation.z() + initialRotation.w() * currentRotation.w();
        maximumAngle = Math.max(maximumAngle, 2 * Math.acos(Math.min(1, Math.abs(dot))));
        observed++;
        if (observed < 3 && !universe.setVelocity(movingId, new PhysicsVector(0, 8, 0),
                new PhysicsVector(0, 1, 0))) {
            throw new IllegalStateException("Covered body lost its three-tick velocity command");
        }
        if (observed % 20 == 0) logProgress("observing");
        if (observed < OBSERVATION_TICKS) return;
        if (maximumDistance < 0.5 || maximumRise < 0.5 || maximumAngle < 0.02) {
            throw new IllegalStateException("Admitted body did not actually translate and rotate");
        }
        finished = true;
        AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_ISOLATION_PASSED ticks={} total={} paused_count={} moving={} "
                        + "max_distance={} max_rise={} max_angle={} terrain={}", observed, runtime.size(),
                pausedCount(), movingId, maximumDistance, maximumRise, maximumAngle, runtime.terrainDiagnostics());
        restore();
    }

    private static Vec3 center(SubLevelPhysicsUniverse.Motion motion) {
        var rotation = motion.pose().rotation();
        var position = motion.pose().position();
        var pose = new StructurePose(new Vec3(position.x(), position.y(), position.z()),
                rotation.x(), rotation.y(), rotation.z(), rotation.w());
        var center = motion.localCenterOfMass();
        return pose.localToWorldPoint(new Vec3(center.x(), center.y(), center.z()));
    }

    private static long pausedCount() {
        return runtime.subLevels().stream().filter(body -> runtime.universe().isSimulationPaused(body.id())).count();
    }

    private static void logProgress(String phase) {
        AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_ISOLATION_PROGRESS phase={} age={} observed={} total={} "
                        + "paused={} moving={} max_distance={} max_rise={} max_angle={} terrain_complete={} terrain={}",
                phase, age, observed, runtime.size(), pausedCount(), movingId, maximumDistance, maximumRise,
                maximumAngle, runtime.terrainCoverageComplete(), runtime.terrainDiagnostics());
    }

    private static void restore() {
        if (player == null) return;
        try {
            if (movingId != null && movingStart != null) {
                runtime.universe().setVelocity(movingId, movingStart.linearVelocity(), movingStart.angularVelocity());
            }
            player.setNoGravity(previousNoGravity);
            player.setDeltaMovement(previousPlayerVelocity);
            player.resetFallDistance();
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Terrain isolation smoke cleanup failed", failure);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (finished) {
            closing = true;
            minecraft.stop();
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        if (!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                .startsWith("native-sublevel-terrain-isolation-")) {
            failed = true;
            finished = true;
            AcademyCraft.LOGGER.error("SUBLEVEL_TERRAIN_ISOLATION_FAILED isolated game directory required");
            return;
        }
        clientReady = true;
    }

    public static boolean complete() { return finished && !failed; }
}
