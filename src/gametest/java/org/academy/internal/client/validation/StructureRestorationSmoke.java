package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.structure.upgrade.lifecycle.MarkedGridRestoration;
import org.academy.internal.common.sublevel.*;
import org.joml.Quaterniond;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in real solver/automatic scheduler fixture; never requests the successful restoration itself. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureRestorationSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureRestorationSmoke");
    private static final String MODE = System.getProperty("academy.structureRestorationSmokeMode", "functional");
    private static final int RETAIN_TICKS = 180;
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Set<BlockPos> PLACED = new LinkedHashSet<>();
    private static volatile boolean ready, complete, failed, closing;
    private static volatile Path directory;
    private static volatile ClientProbe clientProbe;
    private static volatile UUID clientSeen, clientRetired;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity;
    private static float oldYaw, oldPitch;
    private static boolean oldNoGravity;
    private static GameType oldMode;
    private static BlockPos arena, seed, obstruction;
    private static StructureRef reference;
    private static StructureRef restartReference;
    private static UUID transaction, control;
    private static UUID attributedOwner;
    private static List<BlockPos> sourceCells = List.of();
    private static List<StructureGridRestoration.Placement> placements = List.of();
    private static int scenario, stage, stageTicks, age;
    private static String backend = "pending";
    private static int capturedSize;
    private static boolean preservedForRestart;
    private static double settledYaw;
    private static boolean cancelRestoration;
    private static int cancelledEvents, offlineEvents;

    private StructureRestorationSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 6_000, "restoration fixture deadline");
            if (player == null) { prepare(event.getServer()); return; }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            stageTicks++;
            if (MODE.equals("joint") && stage >= 4 && stage <= 6) StructureSableJointSmoke.assertCompanion(player);
            if (stage == 0) { beginCapture(); return; }
            if (stage == 1) { awaitCapture(); return; }
            if (stage == 2) { awaitRest(); return; }
            if (stage == 3) { awaitObstruction(); return; }
            if (stage == 4) { awaitRestoration(); return; }
            if (stage == 5) { awaitClientAndRecapture(); return; }
            if (stage == 6) { awaitRecapture(); return; }
            if (stage == 7) { awaitCancellation(); }
            if (stage == 8) {
                var result = StructureRestorationCrashSmoke.verify(player);
                if (result.isPresent()) {
                    RESULTS.add(result.orElseThrow()); restorePlayer(); write(true, ""); complete = true;
                } else check(stageTicks < 600, "interrupted restoration recovery did not reach its unique owner outcome");
            }
            if (stage == 9) { awaitJoint(); }
            if (stage == 10) { awaitRestart(); }
            if (stage == 11) {
                var result = StructureLocalRestorationSmoke.tick(player);
                if (result.isPresent()) { RESULTS.addAll(result.orElseThrow()); restorePlayer(); write(true, ""); complete = true; }
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void prepare(MinecraftServer server) throws Exception {
        player = server.getPlayerList().getPlayers().getFirst();
        oldPosition = player.position(); oldVelocity = player.getDeltaMovement();
        oldYaw = player.getYRot(); oldPitch = player.getXRot(); oldNoGravity = player.isNoGravity();
        oldMode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.CREATIVE);
        if (MODE.startsWith("admin")) { StructureLocalRestorationSmoke.prepare(player); backend = "academy:sublevel"; nextStage(11); return; }
        if (MODE.equals("verify")) { loadRestart(); return; }
        if (MODE.equals("recover_verify")) {
            StructureRestorationCrashSmoke.prepareVerifier(player);
            backend = StructureRestorationCrashSmoke.backend(); nextStage(8); return;
        }
        check(MODE.equals("functional") || MODE.equals("prepare") || MODE.equals("joint") || MODE.equals("multiblock") || MODE.equals("canopy")
                || StructureRestorationCrashSmoke.crashMode(), "unknown fixture mode");
        arena = new BlockPos((int) Math.floor(oldPosition.x) + 10, player.level().getMaxY() - 48,
                (int) Math.floor(oldPosition.z));
        for (var pos : BlockPos.betweenClosed(arena.offset(-4, -1, -4), arena.offset(51, 9, 4))) {
            player.level().getChunkAt(pos);
            check(player.level().getBlockState(pos).isAir(), "fixture arena occupied: " + pos);
        }
        player.connection.teleport(arena.getX() - 3, arena.getY() + 3, arena.getZ() - 3, 0, 0);
        if (MODE.equals("prepare") || MODE.equals("joint") || StructureRestorationCrashSmoke.crashMode()) scenario = 1;
        if (MODE.equals("multiblock") || MODE.equals("canopy")) scenario = 3;
    }

    private static String name() {
        if (MODE.equals("joint")) return "sable_joint_then_rest";
        if (MODE.equals("multiblock")) return "multiblock_tilted_yaw_then_rest";
        if (MODE.equals("canopy")) return "tilted_rod_on_stepped_leaves";
        return MODE.equals("verify") ? "restart_marked_rest" : switch (scenario) {
            case 0 -> "unmarked_retained";
            case 1 -> "lease_then_rest";
            case 2 -> "occupied_target_then_rest";
            case 3 -> "tilted_yaw_then_rest";
            case 4 -> "offline_owner_then_rest";
            default -> "protection_cancel_then_retry";
        };
    }

    private static Identifier skill() {
        return AcademyCraft.academy(switch (scenario) {
            case 2 -> "magnet_manipulation";
            case 3 -> "high_speed_jet";
            default -> "kinetic_throw";
        });
    }

    private static void beginCapture() {
        attributedOwner = scenario == 4 ? UUID.randomUUID() : player.getUUID();
        cancelledEvents = offlineEvents = 0;
        if (scenario == 4) {
            check(player.level().getServer().getPlayerList().getPlayer(attributedOwner) == null,
                    "offline fixture owner unexpectedly exists online");
            check(org.academy.internal.common.structure.upgrade.lifecycle.StructureRestSettings
                    .get(player.level().getServer()).allowOfflineOwners(), "fixture requires configured offline-owner restoration");
        }
        seed = arena.offset(scenario * 8, 1, 0);
        // Keep the active test platform inside the no-natural-spawn radius in every case.
        player.connection.teleport(seed.getX() - 3, seed.getY() + 3, seed.getZ() - 3, 0, 0);
        for (var pos : BlockPos.betweenClosed(seed.offset(-3, -1, -3), seed.offset(3, -1, 3))) {
            place(pos, MODE.equals("canopy") ? Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true)
                    : Blocks.GLOWSTONE.defaultBlockState());
        }
        sourceCells = MODE.equals("canopy") ? List.of(seed, seed.east(), seed.east(2), seed.east(3))
                : MODE.equals("multiblock") ? List.of(seed, seed.east(), seed.south(), seed.above()) : List.of(seed);
        for (var position : sourceCells) place(position, Blocks.IRON_BLOCK.defaultBlockState());
        if (StructurePayloadFixtureSupport.ENABLED && StructureRestorationCrashSmoke.crashMode()) StructurePayloadFixtureSupport.chest(player, seed);
        var request = new StructureCaptureRequest(sourceCells, 8, 8, StructureCaptureRequest.ALLOW, true);
        if (scenario != 0) request = request.withSkillCreation(attributedOwner, skill());
        var capture = StructureTransactions.capture(player.level(), request);
        check(capture.pending() || capture.succeeded(), "capture rejected: " + capture);
        transaction = capture.transactionId();
        nextStage(1);
    }

    private static void awaitCapture() throws Exception {
        check(stageTicks < 200, "capture timeout");
        var capture = StructureTransactions.poll(player.level().getServer(), transaction).orElseThrow();
        if (capture.pending()) return;
        check(capture.succeeded(), "capture failed: " + capture);
        reference = capture.structure().orElseThrow(); backend = reference.backend().toString();
        var motion = StructureDynamics.motion(player.level(), reference);
        if (motion.isEmpty()) return;
        capturedSize = backendSize();
        for (var position : sourceCells) check(player.level().getBlockState(position).isAir(), "capture retained host source: " + position);
        if (scenario == 0) {
            check(LiveStructureProvenance.healthy(player.level().getServer())
                    && LiveStructureProvenance.metadata(player.level().getServer(), reference).isEmpty(),
                    "generic capture was unexpectedly marked");
        } else {
            var metadata = StructureProvenanceSmokeAssertions.requireSkill(player, reference, skill(), attributedOwner);
            check(metadata.creationOperationId().equals(transaction), "mark is not bound to capture transaction");
            control = UUID.randomUUID();
            if (StructureDirectControl.acquire(player.level(), reference, player, control) != StructureDirectControl.Status.ACTIVE) return;
            if (scenario == 3) tiltFixture();
            if (MODE.equals("canopy")) {
                for (int x = 2; x <= 3; x++) for (int z = -2; z <= 2; z++) {
                    place(seed.offset(x, 0, z), Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true));
                }
            }
        }
        clientSeen = null; clientRetired = null;
        clientProbe = new ClientProbe(reference, List.of());
        if (MODE.equals("prepare")) { saveRestart(); return; }
        if (MODE.equals("joint")) StructureSableJointSmoke.beginCompanion(player, seed);
        nextStage(2);
    }

    private static void awaitRest() {
        check(StructureSpaces.resolve(player.level().getServer(), reference).isPresent(), "retained body disappeared: " + name());
        if (control != null) check(StructureDirectControl.validate(player.level(), reference, player, control)
                == StructureDirectControl.Status.ACTIVE, "fixture lease expired");
        if (MODE.equals("joint") && !StructureSableJointSmoke.companionReady(player)) {
            check(stageTicks < 300, "unmarked companion did not finish capturing"); return;
        }
        if (stageTicks < RETAIN_TICKS) return;
        if (scenario == 0) {
            record(Map.of("waited_ticks", stageTicks, "still_physical", true, "marked", false));
            check(StructureTransactions.discard(player.level().getServer(), reference), "unmarked fixture cleanup failed");
            finishCase(); return;
        }
        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        if (motion.linearVelocity().length() > 0.03 || motion.angularVelocity().length() > 0.03) {
            check(stageTicks < 600, "body did not settle naturally: " + motion);
            return;
        }
        if (!reference.id().equals(clientSeen)) { check(stageTicks < 600, "client never received captured body"); return; }
        settledYaw = Math.toDegrees(Math.atan2(motion.pose().localToWorldDirection(new Vec3(0, 0, 1)).x,
                motion.pose().localToWorldDirection(new Vec3(0, 0, 1)).z));
        StructureDirectControl.release(reference, control); control = null;
        var preview = preview();
        check(preview.status() == StructureOperation.Status.READY && preview.value().isPresent(),
                "resting preview failed: " + preview + " diagnostics=" + previewDiagnostics());
        var plan = preview.value().orElseThrow();
        placements = plan.placements();
        if (StructureRestorationCrashSmoke.crashMode()) StructureRestorationCrashSmoke.arm(player, reference, placements);
        check(placements.size() == (MODE.equals("multiblock") || MODE.equals("canopy") ? 4 : 1), "restoration cell count changed");
        check(placements.stream().map(StructureGridRestoration.Placement::targetPosition).distinct().count() == placements.size(),
                "restoration mapped multiple source cells into one target");
        check(plan.alignedPose().localToWorldDirection(new Vec3(0, 1, 0)).distanceTo(new Vec3(0, 1, 0)) < 1.0e-8,
                "restoration target is not upright");
        var forward = plan.alignedPose().localToWorldDirection(new Vec3(0, 0, 1));
        check(Math.abs(forward.x - Math.rint(forward.x)) < 1.0e-8
                && Math.abs(forward.z - Math.rint(forward.z)) < 1.0e-8, "restoration yaw is not a grid quadrant");
        if (MODE.equals("joint")) {
            StructureSableJointSmoke.attachToWorld(player, reference);
            capturedSize = backendSize();
            nextStage(9);
        } else if (scenario == 2) {
            obstruction = placements.getFirst().targetPosition();
            place(obstruction, Blocks.LIGHT.defaultBlockState());
            nextStage(3);
        } else if (scenario == 5) {
            cancelRestoration = true;
            nextStage(7);
        } else nextStage(4);
    }

    private static void awaitObstruction() {
        check(StructureSpaces.resolve(player.level().getServer(), reference).isPresent(), "occupied-target restoration lost source");
        check(player.level().getBlockState(obstruction).is(Blocks.LIGHT), "automatic restoration overwrote occupied target");
        check(StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world()
                .read(BlockPos.ZERO).state().is(Blocks.IRON_BLOCK), "occupied-target refusal changed plot contents");
        if (stageTicks < RETAIN_TICKS) return;
        check(preview().status() != StructureOperation.Status.READY, "occupied target passed read-only preview");
        player.level().setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
        nextStage(4);
    }

    private static void awaitJoint() {
        StructureSableJointSmoke.assertRetained(player, reference);
        for (var placement : placements) check(player.level().getBlockState(placement.targetPosition()).isAir(),
                "jointed source was written to the world");
        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        check(motion.linearVelocity().length() < .03 && motion.angularVelocity().length() < .03,
                "joint fixture is moving, so retention cannot prove the joint gate: " + motion);
        if (stageTicks < RETAIN_TICKS) return;
        var refused = preview();
        check(refused.status() != StructureOperation.Status.READY && refused.value().isEmpty(),
                "real solver joint passed the restoration preview");
        var schedulerDetail = org.academy.internal.common.structure.upgrade.lifecycle.SkillStructureRestoration
                .detail(player.level().getServer(), reference);
        if (!schedulerDetail.equals(refused.detail())) {
            check(stageTicks < 600, "formal scheduler did not exercise the joint refusal: " + schedulerDetail);
            return;
        }
        StructureSableJointSmoke.removeJoint(player);
        var allowed = preview();
        check(allowed.status() == StructureOperation.Status.READY && allowed.value().isPresent(),
                "joint removal did not restore preview eligibility: " + allowed);
        placements = allowed.value().orElseThrow().placements();
        nextStage(4);
    }

    private static StructureOperation.Result<StructureGridRestoration.Preview> preview() {
        return MarkedGridRestoration.INSTANCE.preview(new StructureGridRestoration.Access(player.level(), reference,
                new StructureGridRestoration.RestorationActor(Optional.of(attributedOwner), MarkedGridRestoration.POLICY_ID)),
                new StructureGridRestoration.Options(StructureGridRestoration.Alignment.UPRIGHT_NEAREST_YAW));
    }

    private static String previewDiagnostics() {
        var body = org.academy.internal.common.structure.StructureBackendRouter.orientationAccess(player.level(), reference)
                .flatMap(access -> access.observe(player.level(), reference)).orElse(null);
        if (body == null) return "body_unavailable";
        var target = org.academy.internal.common.structure.upgrade.lifecycle.RestorationGrid
                .align(body.motion().pose(), List.of(BlockPos.ZERO)).target(BlockPos.ZERO);
        return "terrain_ready=" + body.terrainReady() + ",pose=" + body.motion().pose() + ",target=" + target
                + ",target_state=" + player.level().getBlockState(target)
                + ",entities=" + player.level().getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(target).deflate(1e-5), entity -> !entity.isRemoved()).stream()
                .map(entity -> entity.getType() + "@" + entity.position()).toList();
    }

    private static void awaitCancellation() {
        check(StructureSpaces.resolve(player.level().getServer(), reference).isPresent(), "cancelled restoration lost source body");
        check(StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world()
                .read(BlockPos.ZERO).state().is(Blocks.IRON_BLOCK), "cancelled restoration changed source content");
        for (var placement : placements) check(player.level().getBlockState(placement.targetPosition()).isAir(),
                "cancelled restoration wrote a target block");
        check(LiveStructureProvenance.metadata(player.level().getServer(), reference).isPresent(),
                "cancelled restoration retired its mark");
        if (stageTicks < RETAIN_TICKS) return;
        if (cancelledEvents == 0) { check(stageTicks < 600, "formal scheduler never reached cancellation event"); return; }
        cancelRestoration = false;
        nextStage(4);
    }

    @SubscribeEvent
    public static void restorationEvent(StructureRestorationEvent event) {
        if (!ENABLED || player == null || reference == null || event.host() != player.level()
                || !event.reference().id().equals(reference.id()) || !event.reference().backend().equals(reference.backend())) return;
        if (scenario == 4) {
            check(event.offlineOwner(), "offline maintenance fabricated an online actor");
            check(event.metadata().ownerId().filter(attributedOwner::equals).isPresent(), "offline event changed attribution");
            check(event.host().getServer().getPlayerList().getPlayer(attributedOwner) == null, "offline owner appeared online");
            offlineEvents++;
        }
        if (cancelRestoration) {
            check(scenario == 5, "cancellation escaped its fixture case");
            event.setCanceled(true);
            cancelledEvents++;
        }
    }

    private static void awaitRestoration() {
        if (StructureSpaces.resolve(player.level().getServer(), reference).isPresent()) {
            if (stageTicks % 100 == 0) AcademyCraft.LOGGER.info("STRUCTURE_RESTORATION_WAIT case={} ticks={} motion={} scheduler={} preview={}",
                    name(), stageTicks, StructureDynamics.motion(player.level(), reference),
                    org.academy.internal.common.structure.upgrade.lifecycle.SkillStructureRestoration
                            .detail(player.level().getServer(), reference), preview());
            check(stageTicks < 800, "automatic restoration timeout: " + name());
            return;
        }
        check(!StructureRestorationCrashSmoke.crashMode(), "fault checkpoint failed to interrupt the restoration process");
        check(!placements.isEmpty(), "missing exact placement evidence");
        for (var placement : placements) {
            check(player.level().getBlockState(placement.targetPosition()).is(Blocks.IRON_BLOCK), "restoration lost target content");
            PLACED.add(placement.targetPosition());
        }
        check(LiveStructureProvenance.metadata(player.level().getServer(), reference).isEmpty(), "restored mark was not retired");
        check(StructureDynamics.motion(player.level(), reference).isEmpty(), "restored body still has solver motion");
        check(backendSize() == capturedSize - 1, "restoration did not release backend capacity");
        SubLevelStructureRefs.id(reference).ifPresent(id -> check(SubLevelCollisionRuntime.index(player.level())
                .flatMap(index -> index.entry(id)).isEmpty(), "server collision index retained a ghost body"));
        clientProbe = new ClientProbe(reference, placements.stream().map(StructureGridRestoration.Placement::targetPosition).toList());
        nextStage(5);
    }

    private static void awaitClientAndRecapture() {
        if (!reference.id().equals(clientRetired)) { check(stageTicks < 200, "client did not remove body and receive restored blocks"); return; }
        var details = new LinkedHashMap<String, Object>();
        details.put("automatic_scheduler", true); details.put("placement_count", placements.size());
        details.put("settled_yaw_degrees", settledYaw); details.put("server_body_removed", true);
        details.put("client_body_and_collision_removed", true); details.put("host_blocks_synced", true);
        details.put("backend_capacity_released", true); details.put("retention_ticks", RETAIN_TICKS);
        if (MODE.equals("joint")) details.putAll(StructureSableJointSmoke.result(player));
        if (scenario == 4) {
            check(offlineEvents > 0, "offline restoration did not publish the protection hook");
            details.put("offline_owner_uuid", attributedOwner.toString()); details.put("offline_owner_events", offlineEvents);
            details.put("offline_attribution_created_by_trusted_fixture_api", true);
        }
        if (scenario == 5) {
            check(cancelledEvents > 0 && !cancelRestoration, "protection refusal was not exercised");
            details.put("cancelled_protection_events", cancelledEvents); details.put("retry_after_listener_allowed", true);
        }
        record(details);
        if (MODE.equals("verify")) { cleanup(); write(true, ""); complete = true; return; }
        var targets = placements.stream().map(StructureGridRestoration.Placement::targetPosition).toList();
        var capture = StructureTransactions.capture(player.level(), new StructureCaptureRequest(targets, 8, 8,
                StructureCaptureRequest.ALLOW, true));
        check(capture.pending() || capture.succeeded(), "freed capacity could not be reused");
        transaction = capture.transactionId();
        nextStage(6);
    }

    private static void awaitRecapture() {
        var capture = StructureTransactions.poll(player.level().getServer(), transaction).orElseThrow();
        check(stageTicks < 200, "capacity-reuse capture timeout");
        if (capture.pending()) return;
        check(capture.succeeded(), "capacity-reuse capture failed");
        var reused = capture.structure().orElseThrow();
        check(StructureTransactions.discard(player.level().getServer(), reused), "capacity-reuse cleanup failed");
        finishCase();
    }

    /** Explicit fixture arrangement only; settling and candidacy still use unmodified real physics. */
    private static void tiltFixture() {
        var rotation = MODE.equals("canopy") ? new Quaterniond().rotateZ(Math.toRadians(30))
                : new Quaterniond().rotateY(Math.toRadians(37)).rotateZ(Math.toRadians(20));
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            var id = SubLevelStructureRefs.id(reference).orElseThrow();
            check(runtime.shift(id, new Vec3(0, 2, 0)), "cannot raise tilted fixture");
            check(runtime.rotateTo(id, new StructurePose(Vec3.ZERO, rotation)), "cannot tilt fixture");
        } else SableFixture.tilt(rotation);
    }

    private static int backendSize() {
        return SubLevelStructureRefs.isSubLevel(reference) ? SubLevelServer.runtime(player.level()).orElseThrow().size()
                : SableFixture.size();
    }

    private static void saveRestart() throws Exception {
        var saved = new LinkedHashMap<String, Object>();
        saved.put("id", reference.id().toString()); saved.put("backend", backend);
        saved.put("dimension", reference.dimension().toString()); saved.put("owner", player.getUUID().toString());
        saved.put("operation", transaction.toString()); saved.put("seed", List.of(seed.getX(), seed.getY(), seed.getZ()));
        saved.put("arena", List.of(arena.getX(), arena.getY(), arena.getZ()));
        Files.writeString(directory.resolve("structure-restoration-restart.json"), new GsonBuilder().setPrettyPrinting().create().toJson(saved));
        preservedForRestart = true;
        record(Map.of("mark_saved_before_shutdown", true, "body_retained", true));
        restorePlayer(); write(true, ""); complete = true;
    }

    private static void loadRestart() throws Exception {
        var saved = JsonParser.parseString(Files.readString(directory.resolve("structure-restoration-restart.json"))).getAsJsonObject();
        check(saved.get("owner").getAsString().equals(player.getUUID().toString()), "restart changed player identity");
        restartReference = new StructureRef(UUID.fromString(saved.get("id").getAsString()), Identifier.parse(saved.get("dimension").getAsString()),
                Identifier.parse(saved.get("backend").getAsString()), 0);
        backend = restartReference.backend().toString(); scenario = 1;
        attributedOwner = player.getUUID();
        transaction = UUID.fromString(saved.get("operation").getAsString());
        var data = saved.getAsJsonArray("seed"); seed = new BlockPos(data.get(0).getAsInt(), data.get(1).getAsInt(), data.get(2).getAsInt());
        var location = saved.getAsJsonArray("arena"); arena = new BlockPos(location.get(0).getAsInt(), location.get(1).getAsInt(), location.get(2).getAsInt());
        // Sable reactivates its saved sources asynchronously as the player's host chunks arrive.
        // Enter a dedicated wait stage before returning, so no tick can create a replacement.
        player.connection.teleport(arena.getX() - 3, arena.getY() + 3, arena.getZ() - 3, 0, 0);
        for (var pos : BlockPos.betweenClosed(seed.offset(-3, -1, -3), seed.offset(3, -1, 3))) PLACED.add(pos.immutable());
        nextStage(10);
    }

    private static void awaitRestart() {
        check(stageTicks < 600, "restarted structure did not load into a controllable activation: " + restartReference);
        var loaded = LiveStructureProvenance.liveReference(player.level().getServer(), restartReference).orElse(null);
        if (loaded == null || org.academy.internal.common.structure.StructureBackendRouter
                .probeDirectControl(player.level(), loaded).state() != StructureDirectControl.Probe.State.READY) return;
        var metadata = StructureProvenanceSmokeAssertions.requireSkill(player, loaded, skill());
        check(metadata.creationOperationId().equals(transaction), "restart replaced creation identity");
        if (control == null) control = UUID.randomUUID();
        if (StructureDirectControl.acquire(player.level(), loaded, player, control) != StructureDirectControl.Status.ACTIVE) return;
        reference = loaded;
        capturedSize = backendSize();
        clientProbe = new ClientProbe(reference, List.of());
        nextStage(2);
    }

    private static void finishCase() {
        reference = null; control = null; transaction = null; clientProbe = null; placements = List.of(); obstruction = null;
        cancelRestoration = false;
        if (!MODE.equals("joint") && !MODE.equals("multiblock") && !MODE.equals("canopy") && ++scenario < 6) { nextStage(0); return; }
        cleanup(); write(true, ""); complete = true;
        AcademyCraft.LOGGER.info("STRUCTURE_RESTORATION_SMOKE_PASSED backend={} cases={}", backend, RESULTS.size());
    }

    private static void nextStage(int next) { stage = next; stageTicks = 0; }
    private static void place(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        check(player.level().getBlockState(pos).isAir(), "fixture overwrites existing content: " + pos);
        check(player.level().setBlock(pos, state, 3), "fixture placement failed: " + pos); PLACED.add(pos.immutable());
    }
    private static void record(Map<String, Object> details) {
        var row = new LinkedHashMap<String, Object>(details); row.put("case", name()); row.put("passed", true); RESULTS.add(row);
        AcademyCraft.LOGGER.info("STRUCTURE_RESTORATION_CASE_PASSED {}", row);
    }
    private static void restorePlayer() {
        if (player == null) return;
        player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
        player.setDeltaMovement(oldVelocity); player.setNoGravity(oldNoGravity); player.setGameMode(oldMode);
    }
    private static void cleanup() {
        cancelRestoration = false;
        if (player == null || preservedForRestart) return;
        if (MODE.equals("joint")) StructureSableJointSmoke.cleanup(player);
        if (reference != null && control != null) StructureDirectControl.release(reference, control);
        if (reference != null && StructureSpaces.resolve(player.level().getServer(), reference).isPresent()) {
            StructureTransactions.discard(player.level().getServer(), reference);
        }
        for (var pos : PLACED) player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        restorePlayer();
    }
    private static void write(boolean passed, String failure) {
        if (directory == null) return;
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed); report.put("mode", MODE); report.put("backend", backend);
            report.put("cases", RESULTS); report.put("failure", failure); report.put("server_ticks", age);
            report.put("automatic_restoration_requested_by_fixture", false);
            report.put("tilt_pose_is_fixture_setup_only", true);
            report.put("generic_capture_policy_fixture", true);
            report.put("real_default_skill_and_vm_provenance", "validated by separate default/VM fixtures");
            Files.writeString(directory.resolve("structure-restoration-" + MODE + "-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { AcademyCraft.LOGGER.error("Cannot write restoration smoke result", error); }
    }
    private static void fail(Throwable failure) {
        if (failed || complete) return;
        failed = true;
        AcademyCraft.LOGGER.error("STRUCTURE_RESTORATION_SMOKE_FAILED case={} stage={} ticks={}", name(), stage, stageTicks, failure);
        try { cleanup(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        write(false, failure.toString());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (complete || failed) { closing = true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) return;
        if (StructurePayloadFixtureSupport.ENABLED && minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("Restoration smoke requires isolated native-sublevel-direct-smoke* directory")); return;
        }
        minecraft.player.setNoGravity(true); minecraft.player.setDeltaMovement(Vec3.ZERO); minecraft.player.resetFallDistance();
        var probe = clientProbe;
        if (probe != null) {
            boolean bodyPresent;
            if (SubLevelStructureRefs.isSubLevel(probe.reference())) {
                var id = SubLevelStructureRefs.id(probe.reference()).orElseThrow();
                bodyPresent = !SubLevelClientState.blockState(minecraft.level, id, BlockPos.ZERO).isAir()
                        || SubLevelCollisionRuntime.index(minecraft.level).flatMap(index -> index.entry(id)).isPresent();
            } else bodyPresent = SableFixture.clientPresent(probe.reference());
            if (bodyPresent) clientSeen = probe.reference().id();
            if (!bodyPresent && !probe.targets().isEmpty() && probe.targets().stream().allMatch(pos ->
                    minecraft.level.getBlockState(pos).is(Blocks.IRON_BLOCK))) clientRetired = probe.reference().id();
        }
        ready = true;
    }

    private record ClientProbe(StructureRef reference, List<BlockPos> targets) {}
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    /** Optional dependency is linked only in the Sable fixture run. */
    private static final class SableFixture {
        static int size() {
            return dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level()).getAllSubLevels().size();
        }
        static boolean clientPresent(StructureRef ref) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(Minecraft.getInstance().level);
            return container != null && container.getSubLevel(ref.id()) != null;
        }
        static void tilt(Quaterniond rotation) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            var body = (dev.ryanhcode.sable.sublevel.ServerSubLevel) container.getSubLevel(reference.id());
            var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(player.level());
            physics.getPipeline().teleport(body, new org.joml.Vector3d(body.logicalPose().position()).add(0, 2, 0), rotation);
            physics.updatePose(body); body.updateLastPose();
        }
    }
}
