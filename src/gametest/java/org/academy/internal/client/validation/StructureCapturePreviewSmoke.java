package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real capture transactions and read-only extraction previews, in an opt-in copied world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureCapturePreviewSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureCapturePreviewSmoke");
    private static final boolean DISCONNECTED = Boolean.getBoolean("academy.structureCapturePreviewDisconnected");
    private static final boolean MOTION = Boolean.getBoolean("academy.structureExtractionMotionSmoke");
    private static final StructureExtraction.Limits LIMITS = new StructureExtraction.Limits(8, 32, 1_000, 0, 65_536);
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static final Set<UUID> INITIAL_SABLE_IDS = new HashSet<>();
    private static final Set<UUID> OWNED_SABLE_IDS = new HashSet<>();
    private static final List<Map<String, Object>> SPLIT_CELLS = new ArrayList<>();
    private static final List<Map<String, Object>> NEW_SABLE_OBSERVATIONS = new ArrayList<>();
    private static boolean sableTracking;
    private static int removedSableBodies;
    private static int splitPublicationWaitTicks;
    private static volatile boolean ready, complete, failed, closing;
    private static volatile Path resultPath;
    private static ServerPlayer player;
    private static Vec3 oldPosition, oldVelocity;
    private static float oldYaw, oldPitch;
    private static boolean oldNoGravity;
    private static GameType oldMode;
    private static BlockPos seed;
    private static UUID control, transaction;
    private static StructureRef reference;
    private static StructureCaptureRequest captureRequest;
    private static StructureExtraction.CapturePlan preview;
    private static int tier, age, waitTicks;
    private static boolean impostor;
    private static String backend = "capture_pending";
    private static UUID extractionOperation;
    private static StructureExtraction.Plan extractionPlan;
    private static int motionTicks, observedStableHover;
    private static double observedRise;

    private StructureCapturePreviewSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 1_200, "capture preview fixture deadline");
            if (player == null) { prepare(event.getServer()); return; }
            if (sableTracking) SableContent.collectOwned();
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (extractionOperation != null) { tickMotion(); return; }
            if (transaction == null) { startTier(); return; }
            if (reference == null) {
                var result = StructureTransactions.poll(event.getServer(), transaction).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "capture transaction failed: " + result);
                reference = result.structure().orElseThrow();
                backend = reference.backend().toString();
            }
            var context = StructureExtractions.context(player.level(), reference, player);
            if (context.isEmpty()) { check(++waitTicks < 180, "captured body unavailable"); return; }
            backend = reference.backend().toString();
            var service = StructureExtractions.service();
            var request = new StructureExtraction.BindCaptureRequest(control, preview.planId(), transaction);
            var noLease = service.bindCapture(context.get(), request);
            check(noLease.status() != StructureOperation.Status.READY, "binding granted a missing lease");
            if (StructureDirectControl.acquire(player.level(), reference, player, control) != StructureDirectControl.Status.ACTIVE) {
                check(++waitTicks < 180, "capture lease unavailable");
                return;
            }
            if (impostor) {
                check(org.academy.internal.common.structure.transaction.StructureCaptureCoordinator
                        .completedEvidence(event.getServer(), transaction).isEmpty(),
                        "an unpreviewed capture collected extraction evidence");
                var rejected = service.bindCapture(context.get(), request);
                check(rejected.status() == StructureOperation.Status.DENIED,
                        "different world source with identical local content was accepted: " + rejected);
                record("other_source_capture_rejected", rejected);
                discard();
                impostor = false;
                startCapture(captureRequest);
                return;
            }
            if (DISCONNECTED && !SableContent.sourceChanged()) {
                StructureDirectControl.release(reference, control);
                check(++waitTicks < 180, "Sable did not split the disconnected fixture source");
                return;
            }
            if (DISCONNECTED && SableContent.liveOwnedCount() < 2) {
                // Splitting removes source content before the child's bounds/tracking state
                // necessarily become visible to this post-tick observer. Do not keep a lease
                // while waiting, or the next missing-lease assertion would test our own token.
                StructureDirectControl.release(reference, control);
                check(++splitPublicationWaitTicks <= 40, "Sable split child was not published in the fixture arena");
                return;
            }
            var bound = service.bindCapture(context.get(), request);
            var captureEvidence = org.academy.internal.common.structure.transaction.StructureCaptureCoordinator
                    .completedEvidence(event.getServer(), transaction).orElse(null);
            var actualBody = org.academy.internal.common.structure.StructureBackendRouter
                    .orientationAccess(player.level(), reference)
                    .flatMap(access -> access.observe(player.level(), reference)).orElse(null);
            AcademyCraft.LOGGER.info("STRUCTURE_CAPTURE_PREVIEW_BIND_OBSERVATION tier={} status={} detail={} contextRuntime={} contextContent={} evidenceRuntime={} evidenceContent={} actualRuntime={} actualContent={}",
                    tier, bound.status(), bound.detail(), context.get().runtimeId(), context.get().expectedContentRevision(),
                    captureEvidence == null ? null : captureEvidence.runtimeId(),
                    captureEvidence == null ? null : captureEvidence.contentRevision(),
                    actualBody == null ? null : actualBody.runtimeId(), actualBody == null ? null : actualBody.contentRevision());
            if (DISCONNECTED) {
                check(bound.status() == StructureOperation.Status.STALE && bound.value().isEmpty(),
                        "a split source retained capture authority: " + bound);
                check(captureEvidence != null && actualBody != null
                        && captureEvidence.runtimeId().equals(actualBody.runtimeId())
                        && actualBody.contentRevision() > captureEvidence.contentRevision(),
                        "split rejection has no actual content-revision evidence");
                var world = StructureSpaces.resolve(event.getServer(), reference).orElseThrow().world();
                for (var position : captureRequest.positions()) {
                    var local = position.subtract(seed);
                    var state = world.read(local).state();
                    SPLIT_CELLS.add(Map.of("local", List.of(local.getX(), local.getY(), local.getZ()),
                            "actual", state.toString(), "expected", Blocks.IRON_BLOCK.defaultBlockState().toString()));
                }
                check(world.read(new BlockPos(2, 0, 0)).state().isAir(), "the disconnected cell was not migrated from the parent");
                check(SableContent.liveOwnedCount() >= 2, "the split did not produce a separate owned body");
                record("split_source_stale_rejected", bound);
                cleanup();
                check(OWNED_SABLE_IDS.isEmpty(), "split fixture cleanup left owned bodies");
                writeResult(true, "");
                complete = true;
                AcademyCraft.LOGGER.info("STRUCTURE_CAPTURE_PREVIEW_PASSED backend={} disconnected=true cases={} removedBodies={}",
                        backend, RESULTS.size(), removedSableBodies);
                return;
            }
            if (bound.status() == StructureOperation.Status.TERRAIN_PENDING) {
                StructureDirectControl.release(reference, control);
                check(++waitTicks < 180, "binding coverage stalled: " + bound.detail());
                return;
            }
            check(bound.status() == StructureOperation.Status.READY, "real capture binding failed: " + bound);
            check(bound.value().orElseThrow().reference().equals(reference), "bound another structure");
            var plan = bound.value().orElseThrow();
            check(plan.request().sourcePose().worldOrigin().distanceTo(Vec3.atLowerCornerOf(seed)) < 1.0e-8,
                    "bound source origin changed");
            double finalCenterY = plan.worldPath().getLast().y
                    + plan.request().sourcePose().localToWorldDirection(plan.request().localCenterOfMass()).y;
            double sourceCenterY = plan.request().sourcePose().localToWorldPoint(plan.request().localCenterOfMass()).y;
            check(Math.abs(finalCenterY - sourceCenterY - lift()) < 0.001, "bound target lost source-relative height");
            record("real_capture_binding", bound);
            var repeated = service.bindCapture(context.get(), request);
            check(repeated.status() == StructureOperation.Status.READY
                    && repeated.value().orElseThrow().planId().equals(plan.planId()), "identical binding retry changed identity");
            record("binding_retry", repeated);
            var wrongRuntime = new StructureOperation.Context(player.level(), reference, UUID.randomUUID(),
                    player, context.get().expectedContentRevision());
            var stale = service.bindCapture(wrongRuntime, request);
            check(stale.status() != StructureOperation.Status.READY, "repeat binding skipped runtime validation");
            record("repeat_stale_runtime_rejected", stale);
            var wrongToken = service.bindCapture(context.get(), new StructureExtraction.BindCaptureRequest(
                    UUID.randomUUID(), preview.planId(), transaction));
            check(wrongToken.status() == StructureOperation.Status.DENIED, "binding ignored exact control token");
            record("wrong_control_rejected", wrongToken);
            var otherActor = new ArmorStand(player.level(), seed.getX() - 2, seed.getY(), seed.getZ());
            otherActor.setNoGravity(true);
            check(player.level().addFreshEntity(otherActor), "cannot add second actor");
            try {
                var foreign = service.bindCapture(new StructureOperation.Context(player.level(), reference,
                        context.get().runtimeId(), otherActor, context.get().expectedContentRevision()), request);
                check(foreign.status() == StructureOperation.Status.DENIED, "foreign actor read binding");
                record("foreign_actor_rejected", foreign);
            } finally { otherActor.discard(); }
            if (MOTION) {
                var begun = service.begin(context.get(), new StructureExtraction.BeginRequest(control, plan.planId()));
                check(begun.status() == StructureOperation.Status.RUNNING, "extraction did not start: " + begun);
                extractionOperation = begun.operationId().orElseThrow();
                extractionPlan = plan;
                motionTicks = observedStableHover = 0;
                observedRise = 0;
                var retry = service.begin(context.get(), new StructureExtraction.BeginRequest(control, plan.planId()));
                check(retry.operationId().filter(extractionOperation::equals).isPresent(), "begin retry replaced operation");
                record("extraction_begin", begun);
                return;
            }
            if (tier == 2) {
                var world = StructureSpaces.resolve(event.getServer(), reference).orElseThrow().world();
                var before = world.read(BlockPos.ZERO);
                if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
                    check(world.compareAndSet(BlockPos.ZERO, before, new StructureBlockData(Blocks.GOLD_BLOCK.defaultBlockState()))
                            == StructureWriteResult.APPLIED, "fixture cannot mutate captured content");
                } else SableContent.change();
                check(world.read(BlockPos.ZERO).state().is(Blocks.GOLD_BLOCK), "fixture content change did not reach backend");
                var changed = service.bindCapture(context.get(), request);
                check(changed.status() != StructureOperation.Status.READY, "repeat binding accepted changed content");
                record("repeat_content_change_rejected", changed);
            }
            StructureDirectControl.release(reference, control);
            var released = service.bindCapture(context.get(), request);
            check(released.status() != StructureOperation.Status.READY, "repeat binding accepted released lease");
            record("repeat_released_lease_rejected", released);
            discard();
            transaction = null;
            waitTicks = 0;
            if (++tier == 3) {
                cleanup();
                writeResult(true, "");
                complete = true;
                AcademyCraft.LOGGER.info("STRUCTURE_CAPTURE_PREVIEW_PASSED backend={} cases={}", backend, RESULTS.size());
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void prepare(MinecraftServer server) {
        check(!MOTION || !DISCONNECTED, "Motion and disconnected-source fixtures must run separately");
        player = server.getPlayerList().getPlayers().getFirst();
        oldPosition = player.position();
        oldVelocity = player.getDeltaMovement();
        oldYaw = player.getYRot();
        oldPitch = player.getXRot();
        oldNoGravity = player.isNoGravity();
        oldMode = player.gameMode.getGameModeForPlayer();
        seed = new BlockPos(player.blockPosition().getX() + 5, player.level().getMaxY() - 48,
                player.blockPosition().getZ());
        for (var pos : BlockPos.betweenClosed(seed.offset(-6, -4, -6), seed.offset(13, 12, 8))) {
            player.level().getChunkAt(pos);
            check(player.level().getBlockState(pos).isAir(), "fixture arena not empty: " + pos);
        }
        boolean sable = org.academy.internal.common.structure.StructureBackendRouter.status(server).mode()
                == StructureBackendStatus.Mode.SABLE;
        check(!DISCONNECTED || sable, "Disconnected-source regression requires the Sable backend");
        if (sable) SableContent.snapshotExisting();
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true);
        player.connection.teleport(seed.getX() - 3, seed.getY(), seed.getZ(), oldYaw, oldPitch);
    }

    private static void startTier() {
        control = UUID.randomUUID();
        var cells = DISCONNECTED ? List.of(seed, seed.east(2), seed.south())
                : List.of(seed, seed.east(), seed.east(2), seed.south());
        for (var pos : cells) place(pos, Blocks.IRON_BLOCK.defaultBlockState());
        captureRequest = new StructureCaptureRequest(cells, cells.size(), 3, StructureCaptureRequest.ALLOW, true);
        var service = StructureExtractions.service();
        var actor = new StructureOperation.ActorContext(player.level(), player);
        var request = request(captureRequest);
        var ceiling = seed.east(2).above(2);
        place(ceiling, Blocks.STONE.defaultBlockState());
        var blocked = service.planCapture(actor, request);
        check(blocked.status() == StructureOperation.Status.BLOCKED, "offset limb missed a hard ceiling: " + blocked);
        record("offset_shape_ceiling_blocked", blocked);
        player.level().setBlock(ceiling, Blocks.AIR.defaultBlockState(), 3);
        var plant = seed.east(2).above();
        place(plant, Blocks.SHORT_GRASS.defaultBlockState());
        var soft = service.planCapture(actor, request);
        check(soft.status() == StructureOperation.Status.BLOCKED && soft.detail().startsWith("soft_content_requires_authorization"),
                "soft obstacle was removed or ignored: " + soft);
        check(player.level().getBlockState(plant).is(Blocks.SHORT_GRASS), "read-only preview removed soft content");
        record("soft_content_requires_permission", soft);
        player.level().setBlock(plant, Blocks.AIR.defaultBlockState(), 3);
        var permissionDenied = new StructureCaptureRequest(cells, cells.size(), 3, (host, pos) -> false, true);
        var denied = service.planCapture(actor, request(permissionDenied));
        check(denied.status() == StructureOperation.Status.DENIED, "preview ignored permission predicate");
        record("source_permission_denied", denied);
        if (tier == 0) {
            var blockEntity = seed.east(5);
            place(blockEntity, Blocks.CHEST.defaultBlockState());
            var unsupported = service.planCapture(actor, request(new StructureCaptureRequest(List.of(blockEntity), 1, 1,
                    StructureCaptureRequest.ALLOW, true)));
            check(unsupported.status() == StructureOperation.Status.UNSUPPORTED,
                    "block-entity source has no complete provenance evidence: " + unsupported);
            check(player.level().getBlockState(blockEntity).is(Blocks.CHEST), "preview removed unsupported content");
            record("block_entity_source_rejected", unsupported);
            player.level().setBlock(blockEntity, Blocks.AIR.defaultBlockState(), 3);
            var absent = new BlockPos(20_000_000, seed.getY(), 20_000_000);
            check(player.level().getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null,
                    "unloaded fixture coordinate already loaded");
            var unknown = service.planCapture(actor, request(new StructureCaptureRequest(List.of(absent), 1, 1,
                    StructureCaptureRequest.ALLOW, true)));
            check(unknown.status() == StructureOperation.Status.TERRAIN_PENDING, "missing chunk treated as empty");
            check(player.level().getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null,
                    "preview loaded absent source chunk");
            record("missing_chunk_not_loaded", unknown);
        }
        var result = service.planCapture(actor, request);
        check(result.status() == StructureOperation.Status.READY, "clear source preview failed: " + result);
        preview = result.value().orElseThrow();
        check(preview.worldPath().getFirst().distanceTo(Vec3.atLowerCornerOf(seed)) < 1.0e-8, "preview origin is not source origin");
        check(Math.abs(preview.worldPath().getLast().y - seed.getY() - lift()) < 1.0e-8, "preview lift height mismatch");
        for (var pos : cells) check(player.level().getBlockState(pos).is(Blocks.IRON_BLOCK), "preview mutated source");
        record("source_relative_preview", result);
        if (tier == 0 && !DISCONNECTED) {
            var otherCells = cells.stream().map(pos -> pos.east(7)).toList();
            for (var pos : otherCells) place(pos, Blocks.IRON_BLOCK.defaultBlockState());
            impostor = true;
            startCapture(new StructureCaptureRequest(otherCells, cells.size(), 3, StructureCaptureRequest.ALLOW, true));
        } else startCapture(captureRequest);
    }

    private static StructureExtraction.CaptureRequest request(StructureCaptureRequest capture) {
        return new StructureExtraction.CaptureRequest(control, capture, lift(), 240, 200, LIMITS);
    }

    private static int lift() { return 3 + 2 * tier; }

    private static void tickMotion() {
        check(++motionTicks < 520, "extraction motion timeout");
        var context = StructureExtractions.context(player.level(), reference, player).orElseThrow();
        var service = StructureExtractions.service();
        var receipt = service.status(context, new StructureExtraction.StatusRequest(control, extractionOperation));
        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
        var origin = extractionPlan.request().sourcePose().localToWorldPoint(extractionPlan.request().localCenterOfMass());
        observedRise = motion.pose().localToWorldPoint(motion.localCenterOfMass()).y - origin.y;
        boolean hovering = receipt.value().map(value -> value.phase() == StructureExtraction.Phase.HOVERING).orElse(false);
        if (hovering) {
            check(Math.abs(observedRise - lift()) <= 0.1, "reported hover outside target: " + observedRise);
            observedStableHover++;
        }
        if (motionTicks % 40 == 0) AcademyCraft.LOGGER.info(
                "STRUCTURE_EXTRACTION_MOTION_PROGRESS tier={} ticks={} rise={} hover={} receipt={}",
                tier, motionTicks, observedRise, observedStableHover, receipt);
        check(receipt.status() == StructureOperation.Status.RUNNING || receipt.status() == StructureOperation.Status.COMPLETED,
                "extraction terminated without success: " + receipt);
        if (receipt.status() != StructureOperation.Status.COMPLETED) return;
        check(Math.abs(observedRise - lift()) <= 0.1, "completed extraction height incorrect: " + observedRise);
        check(receipt.value().orElseThrow().stableHoverTicks() == 200, "incomplete 200 tick hover: " + receipt);
        // The final hover sample can be observed as COMPLETED after the service's tick transition.
        check(observedStableHover >= 199, "actual observed hover was too short: " + observedStableHover);
        RESULTS.add(Map.of("scenario", "actual_extraction_and_hover", "tier", tier, "lift", lift(),
                "rise", observedRise, "ticks", motionTicks, "observed_hover_ticks", observedStableHover,
                "reported_hover_ticks", receipt.value().orElseThrow().stableHoverTicks(), "passed", true));
        extractionOperation = null;
        extractionPlan = null;
        discard();
        transaction = null;
        waitTicks = 0;
        if (++tier == 3) {
            cleanup();
            writeResult(true, "");
            complete = true;
            AcademyCraft.LOGGER.info("STRUCTURE_EXTRACTION_MOTION_PASSED backend={} cases={}", backend, RESULTS.size());
        }
    }

    private static void startCapture(StructureCaptureRequest request) {
        var capture = StructureTransactions.capture(player.level(), request);
        check(capture.succeeded() || capture.pending(), "capture rejected: " + capture);
        transaction = capture.transactionId();
        reference = capture.structure().orElse(null);
        if (sableTracking && reference != null) OWNED_SABLE_IDS.add(reference.id());
    }

    private static void record(String scenario, StructureOperation.Result<?> receipt) {
        RESULTS.add(Map.of("scenario", scenario, "tier", tier, "lift", lift(), "status", receipt.status().name(),
                "detail", receipt.detail(), "passed", true));
        AcademyCraft.LOGGER.info("STRUCTURE_CAPTURE_PREVIEW_CASE tier={} scenario={} status={} detail={}",
                tier, scenario, receipt.status(), receipt.detail());
    }

    private static void place(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        check(player.level().getBlockState(position).isAir(), "fixture overwrites non-air: " + position);
        check(player.level().setBlock(position, state, 3), "fixture placement failed: " + position);
        PLACED.add(position.immutable());
    }

    private static void discard() {
        if (reference != null) StructureDirectControl.release(reference, control);
        if (sableTracking) SableContent.cleanupOwned();
        else if (reference != null) {
            check(StructureTransactions.discard(player.level().getServer(), reference), "fixture structure cleanup failed");
        }
        reference = null;
    }

    private static void cleanup() {
        if (player == null) return;
        try {
            discard();
            for (var pos : PLACED) player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        } finally {
            player.connection.teleport(oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
            player.setDeltaMovement(oldVelocity);
            player.setNoGravity(oldNoGravity);
            player.setGameMode(oldMode);
        }
    }

    private static void writeResult(boolean passed, String failure) {
        if (resultPath == null) return;
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed);
            report.put("backend", backend);
            report.put("actual_capture_transactions", true);
            report.put("extraction_motion_exercised", MOTION);
            report.put("disconnected_source_regression", DISCONNECTED);
            report.put("split_source_cells", SPLIT_CELLS);
            report.put("removed_fixture_sable_bodies", removedSableBodies);
            report.put("new_sable_body_observations", NEW_SABLE_OBSERVATIONS);
            report.put("split_publication_wait_ticks", splitPublicationWaitTicks);
            report.put("cases", RESULTS);
            report.put("failure", failure);
            Files.writeString(resultPath, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { AcademyCraft.LOGGER.error("Cannot write capture preview result", error); }
    }

    private static void fail(Throwable failure) {
        if (complete || failed) return;
        failed = true;
        AcademyCraft.LOGGER.error("STRUCTURE_CAPTURE_PREVIEW_FAILED tier={} age={}", tier, age, failure);
        try { cleanup(); } catch (RuntimeException error) { failure.addSuppressed(error); }
        writeResult(false, failure.toString());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (failed || complete) { closing = true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) return;
        var directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("Capture preview smoke requires an isolated native-sublevel-direct-smoke* directory"));
            return;
        }
        resultPath = directory.resolve("structure-capture-preview-result.json");
        minecraft.player.setNoGravity(true);
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.resetFallDistance();
        ready = true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Sable's public Academy view is read-only; use its real plot update/event path in this fixture. */
    private static final class SableContent {
        private static void snapshotExisting() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            check(container != null, "Sable fixture has no container");
            for (var body : container.getAllSubLevels()) INITIAL_SABLE_IDS.add(body.getUniqueId());
            sableTracking = true;
        }

        /** Only new bodies in the verified empty fixture arena, or proven children of owned roots. */
        private static void collectOwned() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            check(container != null, "Sable fixture lost its container");
            var arena = new AABB(Vec3.atLowerCornerOf(seed.offset(-6, -16, -6)),
                    Vec3.atLowerCornerOf(seed.offset(14, 14, 9)));
            for (var body : container.getAllSubLevels()) {
                if (INITIAL_SABLE_IDS.contains(body.getUniqueId())) continue;
                var box = body.boundingBox();
                var worldBox = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
                var logical = body.logicalPose().position();
                boolean descendant = OWNED_SABLE_IDS.contains(body.getSplitFromSubLevel());
                boolean boundsInside = arena.contains(worldBox.getCenter()) && arena.minX <= worldBox.minX
                        && arena.minY <= worldBox.minY && arena.minZ <= worldBox.minZ
                        && arena.maxX >= worldBox.maxX && arena.maxY >= worldBox.maxY && arena.maxZ >= worldBox.maxZ;
                // Sable assembly publishes the actual world COM before refreshing the child's
                // bounding box. Its network tracker may already have cleared splitFrom by now.
                boolean logicalInside = arena.contains(logical.x, logical.y, logical.z);
                if (!body.isRemoved() && (descendant || boundsInside || logicalInside)) {
                    OWNED_SABLE_IDS.add(body.getUniqueId());
                }
                if (DISCONNECTED && NEW_SABLE_OBSERVATIONS.size() < 256) {
                    var observed = new LinkedHashMap<String, Object>();
                    observed.put("age", age);
                    observed.put("id", body.getUniqueId().toString());
                    observed.put("split_from", String.valueOf(body.getSplitFromSubLevel()));
                    observed.put("removed", body.isRemoved());
                    observed.put("logical_position", List.of(logical.x, logical.y, logical.z));
                    observed.put("bounds", List.of(Double.toString(box.minX()), Double.toString(box.minY()),
                            Double.toString(box.minZ()), Double.toString(box.maxX()), Double.toString(box.maxY()),
                            Double.toString(box.maxZ())));
                    observed.put("bounds_inside_arena", boundsInside);
                    observed.put("logical_inside_arena", logicalInside);
                    observed.put("owned", OWNED_SABLE_IDS.contains(body.getUniqueId()));
                    NEW_SABLE_OBSERVATIONS.add(observed);
                    AcademyCraft.LOGGER.info("STRUCTURE_CAPTURE_PREVIEW_NEW_BODY {}", observed);
                }
            }
        }

        private static boolean sourceChanged() {
            var world = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world();
            return captureRequest.positions().stream().anyMatch(position ->
                    !world.read(position.subtract(seed)).state().is(Blocks.IRON_BLOCK));
        }

        private static int liveOwnedCount() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            check(container != null, "Sable fixture lost its container");
            int count = 0;
            for (var id : OWNED_SABLE_IDS) {
                var body = container.getSubLevel(id);
                if (body != null && !body.isRemoved()) count++;
            }
            return count;
        }

        private static void cleanupOwned() {
            collectOwned();
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            check(container != null, "Sable cleanup has no container");
            for (var id : List.copyOf(OWNED_SABLE_IDS)) {
                check(!INITIAL_SABLE_IDS.contains(id), "Refusing to remove a pre-existing Sable body");
                if (container.getSubLevel(id) != null) {
                    var owned = new StructureRef(id, player.level().dimension().identifier(),
                            org.academy.internal.common.structure.StructureBackendRouter.SABLE_ID, 0);
                    check(StructureTransactions.discard(player.level().getServer(), owned),
                            "fixture Sable body cleanup failed: " + id);
                    removedSableBodies++;
                }
                OWNED_SABLE_IDS.remove(id);
            }
        }

        private static void change() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level());
            check(container != null, "Sable fixture has no container");
            var body = container.getSubLevel(reference.id());
            check(body != null, "Sable fixture has no body");
            check(body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO,
                    Blocks.GOLD_BLOCK.defaultBlockState(), 3), "Sable plot rejected fixture mutation");
        }
    }
}
