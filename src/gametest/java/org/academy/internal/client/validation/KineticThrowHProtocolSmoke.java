package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.misaka.MisakaNetworkClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actual C2S H press/release protocol against the default native extraction path. A hold case
 * observes natural release after 200 server ticks; a separate capture tests H launch before
 * expiry, so packet scheduling cannot race the intentional end of that hold window.
 * Only the observer player is frozen. No fixture code applies force, pose or velocity to a body.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class KineticThrowHProtocolSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.kineticHProtocolSmoke");
    private static final boolean SPEED_MILESTONE = Boolean.getBoolean("academy.kineticHSpeedMilestone");
    private static final int ONLY_TIER = Integer.getInteger("academy.kineticHTier", -1);
    private static final String SURFACE = System.getProperty("academy.kineticHSurface", "all");
    private static final String MODE = System.getProperty("academy.kineticHMode", "all");
    private static final int[] CHARGE_TICKS = {4, 24, 44};
    private static final int[] LIFT_HEIGHTS = {3, 5, 7};
    private static final Vec3 LAUNCH_DIRECTION = new Vec3(1, 0, 0);
    private static final List<Case> CASES = new ArrayList<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<BlockPos> OBSTACLES = new ArrayList<>();
    private static volatile boolean clientReady;
    private static volatile boolean complete;
    private static volatile boolean failed;
    private static volatile Command command;
    private static volatile int sentCommand;
    private static volatile int sentAtServerTick;
    private static volatile float aimYaw;
    private static volatile float aimPitch = 90;
    private static boolean closing;
    private static boolean started;
    private static ServerPlayer player;
    private static Path output;
    private static Vec3 previousPosition;
    private static Vec3 previousVelocity;
    private static float previousYaw;
    private static float previousPitch;
    private static boolean previousNoGravity;
    private static GameType previousMode;
    private static boolean previousDestroyBlocks;
    private static BlockPos arena;
    private static BlockPos platform;
    private static int index;
    private static int serial;
    private static int awaitedCommand;
    private static int lastTick = -1;
    private static int startedTick;
    private static int phaseTick;
    private static int chargeTick;
    private static int captureTick;
    private static int firstSettledTick = -1;
    private static int liftedTick = -1;
    private static int controlledHoldTicks;
    private static int flightTick;
    private static int clientPacketCount;
    private static double maximumHeightError;
    private static double launchSpeed;
    private static StructureRef reference;
    private static Vec3 sourceCenter;
    private static Vec3 targetCenter;
    private static Vec3 launchCenter;
    private static Map<String, Object> result;
    private static Phase phase = Phase.PREPARE;

    private KineticThrowHProtocolSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || complete || failed) return;
        var server = event.getServer();
        if (lastTick == server.getTickCount() || server.getPlayerList().getPlayers().isEmpty()) return;
        lastTick = server.getTickCount();
        try {
            if (!started) begin(server);
            check(lastTick - startedTick < CASES.size() * 1_000 + 100, "overall server tick timeout");
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            check(player.isAlive(), "observer player died");
            switch (phase) {
                case PREPARE -> {
                    if (lastTick - phaseTick < 10) return;
                    // Two packets in the same client tick also exercise the server press deduplication.
                    awaitedCommand = send(2, 0);
                    phase = Phase.CHARGE;
                }
                case CHARGE -> {
                    if (sentCommand != awaitedCommand) return;
                    if (chargeTick < 0) chargeTick = sentAtServerTick;
                    if (lastTick - chargeTick < CHARGE_TICKS[current().tier()] + 2) return;
                    awaitedCommand = send(0, 2);
                    captureTick = lastTick;
                    phase = Phase.CAPTURE;
                }
                case CAPTURE -> observeCapture(server);
                case HOLD -> observeHold();
                case AIM_LAUNCH -> {
                    check(lastTick - phaseTick < 60, "launch aim was not acknowledged by the server");
                    if (lastTick - phaseTick < 10 || player.getLookAngle().dot(LAUNCH_DIRECTION) < .999) return;
                    // Vanilla movement and Misaka skill packets are different dispatch paths.
                    // Let the actual client rotation reach the server before pressing H.
                    awaitedCommand = send(2, 2);
                    phaseTick = lastTick;
                    phase = Phase.LAUNCH_REQUEST;
                }
                case LAUNCH_REQUEST -> {
                    check(lastTick - phaseTick < 30, "H C2S launch did not reach the service");
                    var diagnostic = VectorStructureService.diagnostic(player).orElseThrow(
                            () -> new IllegalStateException("H launch lost the held session"));
                    check(reference.equals(diagnostic.reference()), "H launch replaced the capture identity");
                    if (!diagnostic.flying()) return;
                    var motion = motion();
                    launchSpeed = motion.linearVelocity().dot(LAUNCH_DIRECTION);
                    double expected = KineticThrow.throwSpeed(SPEED_MILESTONE);
                    check(launchSpeed >= expected * 0.9 && launchSpeed <= expected * 1.1,
                            "H launch velocity differs from " + expected + ": " + motion.linearVelocity());
                    launchCenter = center(motion);
                    flightTick = lastTick;
                    phase = Phase.FLIGHT;
                }
                case FLIGHT -> observeFlight();
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void begin(MinecraftServer server) {
        check(StructureBackendRouter.nativeEnabled(server), "H regression requires a native-only isolated run");
        check(StructureCaptureExtractions.enabled(), "H regression must use the current extraction gameplay path");
        check(ONLY_TIER >= -1 && ONLY_TIER <= 2, "tier must be -1 or 0..2");
        check(List.of("all", "clean", "vegetation", "irregular").contains(SURFACE), "unknown surface");
        check(List.of("all", "hold", "launch").contains(MODE), "unknown H regression mode");
        for (var surface : List.of("clean", "vegetation")) {
            if (!SURFACE.equals("all") && !SURFACE.equals(surface)) continue;
            for (int tier = 0; tier < 3; tier++) {
                if (ONLY_TIER >= 0 && tier != ONLY_TIER) continue;
                if (!MODE.equals("launch")) CASES.add(new Case(surface, tier, true));
                if (!MODE.equals("hold")) CASES.add(new Case(surface, tier, false));
            }
        }
        if ((SURFACE.equals("all") || SURFACE.equals("irregular")) && !MODE.equals("hold")
                && (ONLY_TIER < 0 || ONLY_TIER == 2)) CASES.add(new Case("irregular", 2, false));
        check(!CASES.isEmpty(), "fixture selected no cases");
        player = server.getPlayerList().getPlayers().getFirst();
        previousPosition = player.position(); previousVelocity = player.getDeltaMovement();
        previousYaw = player.getYRot(); previousPitch = player.getXRot();
        previousNoGravity = player.isNoGravity(); previousMode = player.gameMode.getGameModeForPlayer();
        previousDestroyBlocks = org.academy.internal.common.world.damagesource.DestroyBlocksSetting.isDestroyBlocksEnabled(player);
        org.academy.internal.common.world.damagesource.DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
        int x = player.blockPosition().getX() + 64;
        int z = player.blockPosition().getZ();
        player.level().getChunk(x >> 4, z >> 4);
        int y = Math.min(player.level().getMaxY() - 34,
                Math.max(200, player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 60));
        arena = new BlockPos(x, y, z);
        var system = AbilitySystemServer.getSystem(player);
        var actor = player.getUUID();
        var skill = Skills.KINETIC_THROW.get();
        system.setPlayerAbilityCategory(actor, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(actor, 5);
        system.setPlayerBaseMaxCP(actor, 100_000);
        system.setPlayerStatus(actor, org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        system.setPlayerSkillProficiency(actor, skill, SPEED_MILESTONE ? 3_000 : 0);
        if (!skill.isEnabled(player)) system.toggleSkill(actor, skill.getKeyString());
        check(skill.hasProficiencyMilestone(player, 3) == SPEED_MILESTONE,
                "proficiency policy does not match requested 75/90 speed configuration");
        player.setGameMode(GameType.SURVIVAL);
        startedTick = lastTick;
        started = true;
        prepareCase();
        AcademyCraft.LOGGER.info("KINETIC_H_PROTOCOL_START cases={} speed={} actual_c2s=true assistance=false",
                CASES, KineticThrow.throwSpeed(SPEED_MILESTONE));
    }

    private static void prepareCase() {
        // Release the prior case's service view while retaining its ordinary physical body.
        // Otherwise CAPTURE could accidentally observe a still-flying predecessor before
        // the new real release packet has created its own capture receipt.
        VectorStructureService.clear(player);
        var level = player.level();
        platform = arena.offset(0, 0, index * 64);
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            for (int dy = -2; dy <= 18; dy++) level.setBlock(platform.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
        int solids = 0;
        int plants = 0;
        boolean irregular = current().surface().equals("irregular");
        boolean vegetation = current().surface().equals("vegetation");
        for (int dx = irregular ? -6 : -4; dx <= (irregular ? 6 : 4); dx++) {
            for (int dz = irregular ? -5 : -4; dz <= (irregular ? 5 : 4); dz++) {
                // Remove a 4x4 corner: 13x11 - 16 = 127 independently colliding cells.
                if (irregular && dx <= -3 && dz <= -2) continue;
                var cell = platform.offset(dx, 0, dz);
                boolean crop = vegetation && (dx + dz) % 2 == 0;
                level.setBlock(cell, crop
                        ? Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7)
                        : vegetation ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                solids++;
                if (vegetation) {
                    // Include freshly planted seeds as well as mature crop and short grass.
                    level.setBlock(cell.above(), crop
                            ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, (dx & 1) == 0 ? 0 : 7)
                            : Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    plants++;
                }
            }
        }
        if (irregular) check(solids == 127, "irregular source fixture is not 127 cells");
        AbilitySystemServer.getSystem(player).setPlayerAvailableCP(player.getUUID(), 100_000);
        // Keep the seed in the skill's forward safety half-space. A directly overhead
        // observer would correctly exclude its own supporting cells before capture.
        aimYaw = 0;
        aimPitch = (float) Math.toDegrees(Math.atan2(15.5 + player.getEyeHeight(), 10));
        player.connection.teleport(platform.getX() + 0.5, platform.getY() + 16, platform.getZ() - 9.5,
                aimYaw, aimPitch);
        player.setYHeadRot(aimYaw);
        reference = null; sourceCenter = null; targetCenter = null;
        chargeTick = -1; firstSettledTick = -1; liftedTick = -1;
        controlledHoldTicks = 0; maximumHeightError = 0; launchSpeed = 0;
        OBSTACLES.clear();
        result = new LinkedHashMap<>();
        result.put("surface", current().surface()); result.put("tier", current().tier());
        result.put("mode", current().hold() ? "hold_to_natural_release" : "h_launch");
        result.put("expected_height", LIFT_HEIGHTS[current().tier()]);
        result.put("expected_launch_speed", KineticThrow.throwSpeed(SPEED_MILESTONE));
        result.put("source_solids", solids); result.put("source_soft_plants", plants);
        RESULTS.add(result);
        phaseTick = lastTick;
        phase = Phase.PREPARE;
        AcademyCraft.LOGGER.info("KINETIC_H_PROTOCOL_CASE index={} case={} source={}", index, current(), platform);
    }

    private static void observeCapture(MinecraftServer server) {
        check(lastTick - captureTick < 240, "real H release did not finish native capture");
        if (sentCommand != awaitedCommand) return;
        var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
        if (diagnostic == null || diagnostic.stage() == VectorStructureService.Stage.CAPTURING
                || diagnostic.stage() == VectorStructureService.Stage.FLYING) return;
        reference = diagnostic.reference();
        check(reference != null && SubLevelStructureRefs.isSubLevel(reference), "default H did not create a native SubLevel");
        var observation = StructureDynamics.motion(player.level(), reference).orElse(null);
        if (observation == null) return;
        var cells = VectorStructureService.session(player).orElseThrow().capturedCells();
        check(!cells.isEmpty(), "captured source cells disappeared");
        if (current().surface().equals("irregular")) check(cells.size() == 127,
                "irregular source was truncated: " + cells.size());
        var minimum = new BlockPos(cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                cells.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        sourceCenter = Vec3.atLowerCornerOf(minimum).add(observation.localCenterOfMass());
        targetCenter = sourceCenter.add(0, LIFT_HEIGHTS[current().tier()], 0);
        check(cells.stream().allMatch(cell -> player.level().getBlockState(cell).isAir()),
                "capture committed but source solids remain");
        if (current().surface().equals("vegetation")) {
            check(cells.stream().allMatch(cell -> player.level().getBlockState(cell.above()).isAir()),
                    "soft grass or seed crop remains over an extracted cell");
            var world = StructureSpaces.resolve(server, reference).orElseThrow().world();
            check(cells.stream().map(cell -> world.read(cell.subtract(minimum)).state()).noneMatch(
                    state -> state.is(Blocks.SHORT_GRASS)), "clearable grass was copied into the body");
            // Crops are in structure_motion_protected. They must travel with their support,
            // rather than being destroyed to satisfy the grass-clearance assertion.
            var cropCells = cells.stream().filter(cell -> cell.getY() == platform.getY() + 1).toList();
            check(!cropCells.isEmpty() && cropCells.stream().allMatch(cell ->
                    world.read(cell.subtract(minimum)).state().is(Blocks.WHEAT)),
                    "protected seed/crop content was lost during physical capture");
            result.put("protected_crop_cells", cropCells.size());
        }
        result.put("reference", reference.toString()); result.put("captured_cells", cells.size());
        result.put("solver_mass", observation.mass()); result.put("source_center", sourceCenter.toString());
        result.put("target_center", targetCenter.toString());
        phase = Phase.HOLD;
        observeHold();
    }

    private static void observeHold() {
        var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
        if (diagnostic == null) {
            check(current().hold() && liftedTick >= 0, "hold session disappeared before actual arrival");
            int duration = lastTick - liftedTick;
            check(duration >= 200 && duration <= 205, "hover release did not follow 200 actual server ticks: " + duration);
            check(controlledHoldTicks >= 200, "fewer than 200 controlled server tick observations: " + controlledHoldTicks);
            result.put("release_server_tick", lastTick); result.put("hold_elapsed_server_ticks", duration);
            result.put("controlled_hold_observations", controlledHoldTicks);
            casePassed();
            return;
        }
        check(reference.equals(diagnostic.reference()) && !diagnostic.flying(), "hold identity changed or launched without H");
        var observation = motion();
        var actual = center(observation);
        double error = actual.distanceTo(targetCenter);
        boolean settled = error <= 0.1 + 1.0e-6 && observation.linearVelocity().length() <= 0.15 + 1.0e-6
                && observation.angularVelocity().length() <= 0.1 + 1.0e-6;
        if (settled && firstSettledTick < 0) firstSettledTick = lastTick;
        if (diagnostic.lifted() && liftedTick < 0) {
            check(settled, "service started its hover timer before physical arrival: error=" + error
                    + " linear=" + observation.linearVelocity() + " angular=" + observation.angularVelocity());
            liftedTick = lastTick;
            result.put("first_physical_settled_server_tick", firstSettledTick);
            result.put("lifted_server_tick", liftedTick);
            result.put("arrival_after_release_server_ticks", liftedTick - captureTick);
            AcademyCraft.LOGGER.info("KINETIC_H_PROTOCOL_ARRIVED index={} tick={} center={} error={} motion={}",
                    index, lastTick, actual, error, observation.linearVelocity());
        }
        check(lastTick - captureTick < 800, "physical lift never completed");
        if (liftedTick < 0) return;
        check(diagnostic.controlActive(), "physical hover lost control while the timer was active");
        maximumHeightError = Math.max(maximumHeightError, Math.abs(actual.y - targetCenter.y));
        check(error <= 0.15, "body drifted from its completed lift during hover: " + error);
        controlledHoldTicks++;
        if (current().hold()) {
            check(lastTick - liftedTick <= 205, "hover deadline was extended by repeated ticks");
            return;
        }
        if (lastTick - liftedTick < 10) return;
        if (current().surface().equals("irregular")) {
            // The 127-cell body crosses two ordinary five-block stone walls before 64 blocks.
            // No fixture mutation clears these; the real projectile path must break them.
            for (int dx = 12; dx <= 13; dx++) for (int dz = -2; dz <= 2; dz++) {
                var cell = platform.offset(dx, LIFT_HEIGHTS[current().tier()], dz);
                player.level().setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
                OBSTACLES.add(cell);
            }
        }
        // Keep the reticle away from the held structure: H must launch the owned hover.
        aimYaw = -90; aimPitch = 0;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), aimYaw, aimPitch);
        player.setYHeadRot(aimYaw);
        phaseTick = lastTick;
        phase = Phase.AIM_LAUNCH;
    }

    private static void observeFlight() {
        int elapsed = lastTick - flightTick;
        var observation = motion();
        double travel = center(observation).subtract(launchCenter).dot(LAUNCH_DIRECTION);
        if (elapsed < 8) return;
        double expected = KineticThrow.throwSpeed(SPEED_MILESTONE);
        check(travel > expected * elapsed / 20.0 * 0.75,
                "H produced a flying session but actual structure was not propelled: travel=" + travel);
        check(observation.linearVelocity().dot(LAUNCH_DIRECTION) > expected * 0.75,
                "launched body lost forward velocity in open/destructible space: " + observation.linearVelocity());
        check(OBSTACLES.stream().allMatch(cell -> player.level().getBlockState(cell).isAir()),
                "127-cell projectile was blocked by a breakable wall before 64 blocks");
        result.put("launch_projected_speed", launchSpeed); result.put("flight_elapsed_server_ticks", elapsed);
        result.put("flight_forward_distance", travel); result.put("flight_velocity", observation.linearVelocity().toString());
        result.put("destructible_wall_cells", OBSTACLES.size()); result.put("actual_h_c2s", true);
        casePassed();
    }

    private static void casePassed() {
        result.put("max_hover_height_error", maximumHeightError); result.put("passed", true);
        AcademyCraft.LOGGER.info("KINETIC_H_PROTOCOL_CASE_PASSED index={} result={}", index, result);
        if (++index < CASES.size()) {
            prepareCase();
            return;
        }
        try {
            writeResult(true, "passed", null);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write H protocol evidence", failure);
        }
        restore();
        complete = true;
        AcademyCraft.LOGGER.info("KINETIC_H_PROTOCOL_PASSED cases={} actual_c2s=true assistance=false", RESULTS.size());
    }

    private static int send(int presses, int releases) {
        var next = new Command(++serial, presses, releases);
        command = next;
        return next.serial();
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (failed || complete) {
            closing = true;
            minecraft.stop();
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        try {
            var directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
            check(directory.getParent() != null && directory.getParent().getFileName().toString().equals("run")
                    && directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),
                    "real H fixture requires an isolated run/native-sublevel-direct-smoke* directory");
            if (output == null) {
                output = directory.resolve("kinetic-h-protocol-result.json");
                writeResult(false, "running", null);
                org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
            }
            if (started) {
                minecraft.player.setNoGravity(true);
                minecraft.player.setDeltaMovement(Vec3.ZERO);
                minecraft.player.resetFallDistance();
                minecraft.player.setYRot(aimYaw); minecraft.player.setXRot(aimPitch);
                minecraft.player.connection.send(new ServerboundMovePlayerPacket.Rot(aimYaw, aimPitch,
                        minecraft.player.onGround(), minecraft.player.horizontalCollision));
            }
            var next = command;
            if (next != null && sentCommand != next.serial()) {
                for (int i = 0; i < next.presses(); i++) MisakaNetworkClient.send(KineticThrow.PressPacket.INSTANCE);
                for (int i = 0; i < next.releases(); i++) MisakaNetworkClient.send(KineticThrow.ReleasePacket.INSTANCE);
                clientPacketCount += next.presses() + next.releases();
                sentAtServerTick = minecraft.getSingleplayerServer().getTickCount();
                sentCommand = next.serial();
            }
            clientReady = true;
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void fail(Throwable failure) {
        if (failed || complete) return;
        try {
            if (output != null) writeResult(false, "failed", failure);
        } catch (IOException reportFailure) {
            failure.addSuppressed(reportFailure);
        }
        AcademyCraft.LOGGER.error("KINETIC_H_PROTOCOL_FAILED case={} phase={} server_tick={}", index, phase, lastTick, failure);
        restore();
        failed = true;
    }

    private static void writeResult(boolean passed, String status, Throwable failure) throws IOException {
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed); report.put("status", status);
        report.put("actual_h_c2s", true); report.put("structure_assistance", false);
        report.put("extraction_gameplay_enabled", StructureCaptureExtractions.enabled());
        report.put("speed_milestone", SPEED_MILESTONE); report.put("expected_speed", KineticThrow.throwSpeed(SPEED_MILESTONE));
        report.put("client_packets_emitted", clientPacketCount); report.put("server_tick", lastTick);
        report.put("surface", SURFACE); report.put("mode", MODE); report.put("only_tier", ONLY_TIER);
        report.put("current_case", index); report.put("phase", phase.toString());
        report.put("cases", List.copyOf(RESULTS)); report.put("failure", failure == null ? "" : failure.toString());
        report.put("failure_stack", failure == null ? List.of()
                : Arrays.stream(failure.getStackTrace()).map(StackTraceElement::toString).toList());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void restore() {
        if (player == null || !player.level().getServer().isSameThread()) return;
        player.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z, previousYaw, previousPitch);
        player.setDeltaMovement(previousVelocity); player.setNoGravity(previousNoGravity);
        player.setGameMode(previousMode); player.resetFallDistance();
        org.academy.internal.common.world.damagesource.DestroyBlocksSetting.setDestroyBlocksEnabled(player, previousDestroyBlocks);
    }

    private static StructureDynamics.State motion() {
        return StructureDynamics.motion(player.level(), reference).orElseThrow(
                () -> new IllegalStateException("native physics observation missing for " + reference));
    }

    private static Vec3 center(StructureDynamics.State observation) {
        return observation.pose().localToWorldPoint(observation.localCenterOfMass());
    }

    private static Case current() { return CASES.get(index); }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Phase { PREPARE, CHARGE, CAPTURE, HOLD, AIM_LAUNCH, LAUNCH_REQUEST, FLIGHT }

    private record Case(String surface, int tier, boolean hold) {}
    private record Command(int serial, int presses, int releases) {}
}
