package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real default service paths on either backend; no fabricated capture request for the three skills. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureDefaultProvenanceSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureDefaultProvenanceSmoke");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final StructureOrientation.Limits ROTATION_LIMITS = new StructureOrientation.Limits(
            3, 12, 5_000, 200, .01, .03, 0, 65_536);
    private static final StructurePose ROTATION = OrientationTargets.axisAngle(new Vec3(0, 1, 0), Math.PI / 6);
    private static volatile boolean ready, complete, failed, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity, anchor;
    private static float oldYaw, oldPitch;
    private static boolean oldNoGravity;
    private static GameType oldMode;
    private static BlockPos seed;
    private static StructureRef reference;
    private static HighSpeedJetNozzle nozzle;
    private static HighSpeedJetStructureService.LaunchHandle jet;
    private static MagneticStructureService.Session magnetic;
    private static UUID capture, control, operation;
    private static int scenario, age, caseTicks;
    private static boolean started;
    private static String backend = "pending";

    private StructureDefaultProvenanceSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 1_600, "default provenance fixture timed out");
            if (player == null) { preparePlayer(event.getServer()); return; }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            check(++caseTicks < 450, "default service failed to complete scenario=" + scenario);
            if (!started) {
                // Let the real teleport acknowledgement settle before the service starts.
                if (caseTicks < 8) return;
                begin(); started = true; return;
            }
            if (scenario == 3) { unmarkedRotation(); return; }
            boolean arrived;
            if (scenario == 0) {
                var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
                reference = diagnostic.reference();
                arrived = diagnostic.lifted() && diagnostic.controlActive() && !diagnostic.flying();
            } else if (scenario == 1) {
                check(magnetic.tick(player, Vec3.atCenterOf(seed).add(0, 1, 0), new Vec3(0, 1, 0), .8, .2, 32, 0),
                        "default magnetic session ended");
                reference = magnetic.reference();
                arrived = magnetic.stage() != MagneticStructureService.Stage.CAPTURING && magnetic.controlActive()
                        && !StructureCaptureExtractions.tracked(reference);
            } else {
                var diagnostic = HighSpeedJetStructureService.diagnostic(player).orElseThrow();
                reference = diagnostic.reference();
                arrived = diagnostic.stage() != HighSpeedJetStructureService.Stage.CAPTURING && diagnostic.controlActive()
                        && !StructureCaptureExtractions.tracked(reference);
            }
            if (!arrived) return;
            var metadata = StructureProvenanceSmokeAssertions.requireSkill(player, reference, AcademyCraft.academy(skill()));
            var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
            double rise = motion.pose().localToWorldPoint(motion.localCenterOfMass()).y - Vec3.atCenterOf(seed).y;
            check(Math.abs(rise - (scenario == 0 ? 3 : 1)) <= .2, "default service bypassed source-relative extraction: " + rise);
            check(player.level().getBlockState(seed).isAir(), "default capture retained the host source");
            backend = reference.backend().toString();
            RESULTS.add(Map.of("skill", skill(), "backend", backend, "default_service", true,
                    "provenance_verified", true, "operation", metadata.creationOperationId().toString(),
                    "actual_rise", rise, "ticks", caseTicks, "passed", true));
            finishCase();
        } catch (Throwable error) { fail(error); }
    }

    private static void preparePlayer(MinecraftServer server) {
        player = server.getPlayerList().getPlayers().getFirst();
        oldPosition = player.position(); oldVelocity = player.getDeltaMovement();
        oldYaw = player.getYRot(); oldPitch = player.getXRot(); oldNoGravity = player.isNoGravity();
        oldMode = player.gameMode.getGameModeForPlayer();
        prepareCase();
    }

    private static void prepareCase() {
        seed = new BlockPos((int) Math.floor(oldPosition.x) + 6, player.level().getMaxY() - 48, (int) Math.floor(oldPosition.z));
        for (var position : BlockPos.betweenClosed(seed.offset(-7, -5, -5), seed.offset(5, 8, 5))) {
            player.level().getChunkAt(position);
            check(player.level().getBlockState(position).isAir(), "default fixture arena occupied: " + position);
        }
        var system = AbilitySystemServer.getSystem(player);
        var category = scenario == 0 ? AbilityCategories.ACCELERATOR.get()
                : scenario == 1 ? AbilityCategories.ELECTROMASTER.get() : AbilityCategories.AEROMANIP.get();
        var skill = scenario == 0 ? Skills.KINETIC_THROW.get() : scenario == 1 ? Skills.MAGNET_MANIPULATION.get() : Skills.HIGH_SPEED_JET.get();
        system.setPlayerAbilityCategory(player.getUUID(), category);
        system.setPlayerLevel(player.getUUID(), 5); system.setPlayerBaseMaxCP(player.getUUID(), 100_000);
        system.setPlayerAvailableCP(player.getUUID(), 100_000);
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        if (scenario >= 2) {
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(player.getUUID(), system.getAeromanipResourceManager().getCapacity(player));
        }
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.connection.teleport(seed.getX() - 5, seed.getY(), seed.getZ() + .5, -90, 0);
        check(player.level().setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3), "fixture seed placement failed");
        caseTicks = 0; started = false;
    }

    private static void begin() {
        check(StructureCaptureExtractions.enabled(), "default fixture requires actual gameplay extraction routing");
        if (scenario == 0) {
            var plan = VectorStructureService.planCapture(player, seed, 0).orElseThrow();
            check(VectorStructureService.beginCapture(player, plan), "default vector capture rejected");
        } else if (scenario == 1) {
            magnetic = MagneticStructureService.begin(player.level(), player, List.of(seed)).orElseThrow();
        } else if (scenario == 2) {
            nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
            // The default service activates thrust in the same handoff tick. Keep that thrust
            // horizontal so the source-relative lift assertion measures extraction, not launch.
            nozzle.attach(player.getUUID(), seed, Direction.WEST);
            check(player.level().addFreshEntity(nozzle), "default jet nozzle rejected");
            jet = HighSpeedJetStructureService.launch(player, new HighSpeedJetStructureService.LaunchRequest(
                    seed, Direction.WEST, null, 2, 1, 120, true), 1).orElseThrow();
        } else {
            var result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                    List.of(seed), 8, 8, StructureCaptureRequest.ALLOW, true));
            check(result.pending() || result.succeeded(), "generic unmarked capture rejected"); capture = result.transactionId();
        }
    }

    private static void unmarkedRotation() {
        if (reference == null) {
            var result = StructureTransactions.poll(player.level().getServer(), capture).orElseThrow();
            if (result.pending()) return;
            check(result.succeeded(), "generic capture failed"); reference = result.structure().orElseThrow();
        }
        check(LiveStructureProvenance.healthy(player.level().getServer())
                && LiveStructureProvenance.metadata(player.level().getServer(), reference).isEmpty(),
                "ACR control marked an existing unmarked body");
        if (control == null) control = UUID.randomUUID();
        if (StructureDirectControl.acquire(player.level(), reference, player, control) != StructureDirectControl.Status.ACTIVE) return;
        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        if (anchor == null) anchor = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        check(StructureDirectControl.submit(player.level(), reference, player, control, new StructurePhysicsControl.Anchor(
                motion.localCenterOfMass(), anchor, Vec3.ZERO, 800 * motion.mass(), 80 * motion.mass(), 5_000)),
                "unmarked control anchor rejected");
        var context = StructureOrientations.context(player.level(), reference, player).orElseThrow();
        var result = operation == null ? StructureOrientations.service().rotateTo(context,
                new StructureOrientation.RotateRequest(control, ROTATION, ROTATION_LIMITS))
                : StructureOrientations.service().status(context, new StructureOrientation.StatusRequest(control, operation));
        if (result.status() == StructureOperation.Status.TERRAIN_PENDING) return;
        if (operation == null) operation = result.operationId().orElseThrow();
        if (result.status() == StructureOperation.Status.RUNNING) return;
        check(result.status() == StructureOperation.Status.COMPLETED, "unmarked rotation failed: " + result);
        double error = OrientationTargets.rotationError(motion.pose(), ROTATION).length();
        check(error <= .02, "unmarked rotation did not reach the target: " + error);
        check(LiveStructureProvenance.metadata(player.level().getServer(), reference).isEmpty(), "rotation retroactively marked old content");
        backend = reference.backend().toString();
        RESULTS.add(Map.of("case", "existing_unmarked_after_control_rotation", "backend", backend,
                "actual_rotate_to", true, "rotation_error", error, "still_unmarked", true, "passed", true));
        finishCase();
    }

    private static String skill() { return switch (scenario) { case 0 -> "kinetic_throw"; case 1 -> "magnet_manipulation"; default -> "high_speed_jet"; }; }
    private static void finishCase() {
        cleanupBody();
        if (++scenario < 4) { prepareCase(); return; }
        restorePlayer(); write(true, ""); complete = true;
        AcademyCraft.LOGGER.info("STRUCTURE_DEFAULT_PROVENANCE_PASSED backend={} cases={} results={}", backend, RESULTS.size(), RESULTS);
    }
    private static void cleanupBody() {
        if (player == null) return;
        if (jet != null) { jet.close(); jet = null; }
        if (magnetic != null) { magnetic.close(); magnetic = null; }
        VectorStructureService.clear(player); HighSpeedJetStructureService.clear(player);
        if (nozzle != null) { nozzle.discard(); nozzle = null; }
        if (reference != null) {
            if (control != null) StructureDirectControl.release(reference, control);
            StructureCaptureExtractions.release(player, reference);
            if (StructureSpaces.resolve(player.level().getServer(), reference).isPresent()) {
                check(StructureTransactions.discard(player.level().getServer(), reference), "fixture body cleanup failed");
            }
        }
        if (seed != null) player.level().setBlock(seed, Blocks.AIR.defaultBlockState(), 3);
        reference = null; control = null; operation = null; capture = null; anchor = null;
    }
    private static void restorePlayer() {
        if (player == null) return;
        player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
        player.setDeltaMovement(oldVelocity); player.setNoGravity(oldNoGravity); player.setGameMode(oldMode);
    }
    private static void write(boolean passed, String failure) {
        if (directory == null) return;
        try {
            Files.writeString(directory.resolve("structure-default-provenance-result.json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("passed", passed, "backend", backend, "cases", RESULTS, "failure", failure,
                            "real_default_service_entries", true, "actual_keyboard_or_network_packets", false, "server_ticks", age)));
        } catch (Exception error) { AcademyCraft.LOGGER.error("Cannot write default provenance smoke result", error); }
    }
    private static void fail(Throwable failure) {
        if (failed || complete) return; failed = true;
        AcademyCraft.LOGGER.error("STRUCTURE_DEFAULT_PROVENANCE_FAILED scenario={} ticks={}", scenario, caseTicks, failure);
        try { cleanupBody(); restorePlayer(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        write(false, failure.toString());
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (complete || failed) { closing = true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) return;
        directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("Default provenance smoke requires an isolated fixture directory")); return;
        }
        minecraft.player.setNoGravity(true); minecraft.player.setDeltaMovement(Vec3.ZERO); minecraft.player.resetFallDistance(); ready = true;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
