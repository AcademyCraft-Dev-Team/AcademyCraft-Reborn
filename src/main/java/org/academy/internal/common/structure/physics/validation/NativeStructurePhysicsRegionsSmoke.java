package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsAttachment;
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.academy.internal.common.structure.space.NativeStructureManifest;
import org.joml.Quaterniond;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit two-JVM fixture for independent regions in the same real host world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructurePhysicsRegionsSmoke {
    public static final String WORLD = "native-structure-physics-regions-smoke";
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructurePhysicsRegionsSmoke");
    private static final String STAGE = System.getProperty("academy.nativeStructurePhysicsRegionsStage", "");
    private static final BlockPos CELL = new BlockPos(0, 80, 0), STONE = CELL.east();
    private static final Vec3 CENTER = Vec3.atCenterOf(CELL);
    private static final AABB BOUNDS_A = new AABB(-2, 108, -2, 14, 128, 14);
    private static final AABB BOUNDS_A_MIGRATED = new AABB(-2, 106, -2, 14, 126, 14);
    private static final AABB BOUNDS_B = new AABB(16, 108, -2, 32, 128, 14);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final long PID = ProcessHandle.current().pid();
    private static final long PROCESS_START = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static NativeStructurePhysicsRegionsSmoke active;
    private enum Phase { ACTIVATE, INITIAL, PARKED, RELOADED, SETTLE, MIGRATE, AFTER_MIGRATE, LIFT, KICK, SAVED,
        RESUME_A, A_ONLY, MISMATCH_B, RESUME_B, BOTH, CLOSE_A, CLEAR, FINISHED }

    private final MinecraftServer server;
    private final ServerLevel host;
    private final NativeStructureRuntime runtime;
    private final Path directory;
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final StringBuilder report = new StringBuilder("normal_shutdown_only=true\nserver_fixture_actions=true\npermanent_fixture_slots=2\n");
    private StructureHandle a, b, unloaded;
    private StructurePhysicsRegion regionA, regionB;
    private UUID idA, idB, oldRuntime;
    private CompoundTag oracle;
    private Phase phase = Phase.ACTIVATE;
    private int ticks, age, sleepingTicks;
    private long stepsA, stepsB;
    private StructurePose poseA, poseB, migrationPose;
    private boolean complete, failed, frozen, stopping, stoppingGuardsVerified, watching;

    private NativeStructurePhysicsRegionsSmoke(MinecraftServer server) {
        this.server = server; directory = verifyDirectory(server);
        host = server.overworld(); runtime = NativeStructureRuntime.backend(server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (!ENABLED) return;
        require(List.of("prepare", "resume").contains(STAGE), "Unknown multi-region probe stage");
        require(active == null && event.getServer().isDedicatedServer(), "Multi-region probe requires a dedicated server");
        // Only a verified disposable directory may arm fixture mutations and controlled JVM exit.
        var scenario = new NativeStructurePhysicsRegionsSmoke(event.getServer()); active = scenario;
        scenario.guarded(scenario::initialize);
    }

    private void initialize() {
        report.append("stage=").append(STAGE).append("\npid=").append(PID).append("\nprocess_start=").append(PROCESS_START).append('\n');
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 0; z++) {
            if (host.setChunkForced(x, z, true)) tickets.add(new ChunkPos(x, z));
            host.getChunk(x, z);
        }
        if (STAGE.equals("prepare")) {
            require(StructurePhysics.savedRegions(host).isEmpty(), "Fresh fixture already owns a saved region");
            for (var pos : BlockPos.betweenClosed(new BlockPos(-1, 109, -1), new BlockPos(31, 127, 13))) {
                require(host.getBlockState(pos).isAir(), "Refusing to replace existing host content at " + pos);
            }
            for (int offset : new int[]{0, 18}) {
                for (var pos : BlockPos.betweenClosed(new BlockPos(3 + offset, 110, 3), new BlockPos(9 + offset, 110, 9))) {
                    require(host.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS), "Cannot create isolated platform");
                }
            }
            a = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow();
            b = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow();
        } else {
            try { oracle = NbtIo.readCompressed(directory.resolve("regions-checkpoint.nbt"), NbtAccounter.create(262144)); }
            catch (IOException error) { throw new IllegalStateException("Cannot read the preceding JVM's region evidence", error); }
            require(oracle.getIntOr("schema", -1) == 1, "Unexpected region evidence schema");
            require(oracle.getLongOr("pid", -1) != PID || oracle.getLongOr("process_start", -1) != PROCESS_START,
                    "Recovery must run in a new real JVM");
            idA = UUID.fromString(oracle.getCompoundOrEmpty("a").getStringOr("region", ""));
            idB = UUID.fromString(oracle.getCompoundOrEmpty("b").getStringOr("region", ""));
            a = runtime.resolve(readRef(oracle.getCompoundOrEmpty("a"))).orElseThrow();
            b = runtime.resolve(readRef(oracle.getCompoundOrEmpty("b"))).orElseThrow();
            require(!a.runtimeId().toString().equals(oracle.getCompoundOrEmpty("a").getStringOr("runtime", ""))
                    && !b.runtimeId().toString().equals(oracle.getCompoundOrEmpty("b").getStringOr("runtime", "")),
                    "New JVM reused an old structure runtime identity");
            // Production already opens saved regions in its NORMAL ServerStarted listener.
            // Freeze only this disposable fixture until both storage activations finish, so
            // neither member can miss the first-step observation while the other is loading.
            regionA = StructurePhysics.attachedRegion(host, a.reference()).orElseThrow();
            regionB = StructurePhysics.attachedRegion(host, b.reference()).orElseThrow();
            require(regionA.id().equals(idA) && regionB.id().equals(idB), "Automatic recovery changed saved region identities");
            require(diagnostics(regionA).steps() == 0 && diagnostics(regionB).steps() == 0,
                    "Saved region stepped before the server-started boundary");
            server.tickRateManager().setFrozen(true); frozen = true;
            report.append("different_jvm=true\nnew_structure_runtimes=true\n");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void beforePhysicsTick(ServerTickEvent.Post event) {
        var scenario = active;
        if (!ENABLED || !STAGE.equals("resume") || scenario == null || scenario.server != event.getServer()
                || scenario.complete || scenario.failed || scenario.phase != Phase.ACTIVATE) return;
        scenario.guarded(scenario::prepareAutomaticResume);
    }

    private void prepareAutomaticResume() {
        if (a.lifecycle() != StructureLifecycle.ACTIVE || b.lifecycle() != StructureLifecycle.ACTIVE) return;
        require(frozen && diagnostics(regionA).steps() == 0 && diagnostics(regionB).steps() == 0,
                "Activation barrier did not preserve the first restored physics step");
        verifyRegionInfo(); assertSaved(a, idA, "a"); assertSaved(b, idB, "b"); assertContents();
        var before = runtime.savedPhysicsEntries(host);
        expectStateRejected(() -> StructurePhysics.savedRegionBounds(host), "Singular bounds silently selected one of two regions");
        expectStateRejected(() -> StructurePhysics.resumeRegion(host), "Singular resume silently selected one of two regions");
        expectStateRejected(() -> StructurePhysics.discardSavedRegion(host), "Singular discard removed multiple regions");
        require(runtime.savedPhysicsEntries(host).equals(before), "Ambiguous legacy calls changed saved checkpoints");
        var absentId = new UUID(0, 1); require(!absentId.equals(idA) && !absentId.equals(idB), "Reserved unknown fixture UUID collided");
        expectStateRejected(() -> StructurePhysics.resumeRegion(host, absentId), "Unknown UUID resumed another saved region");
        require(!StructurePhysics.discardSavedRegion(host, absentId), "Unknown UUID discarded another saved region");
        expectStateRejected(() -> StructurePhysics.resumeRegion(host, idA), "Explicit resume replaced automatically opened region A");
        expectStateRejected(() -> StructurePhysics.resumeRegion(host, idB), "Explicit resume replaced automatically opened region B");
        require(runtime.savedPhysicsEntries(host).equals(before), "Unknown or duplicate UUID calls changed an automatic region");
        require(StructurePhysics.attachedRegion(host, a.reference()).orElseThrow() == regionA
                        && StructurePhysics.attachedRegion(host, b.reference()).orElseThrow() == regionB,
                "Duplicate recovery replaced a live region handle");
        require(regionA.bounds().equals(readBounds(oracle.getCompoundOrEmpty("a").getCompoundOrEmpty("bounds"))),
                "Automatic recovery lost region A's rebased bounds");
        // The automatically restored B must fail closed on a digest mismatch while A proceeds.
        // Mutate before its first native step; changing an already running body is ordinary rebuild.
        put(b, CELL, new StructureBlockData(Blocks.STONE_SLAB.defaultBlockState()));
        report.append("automatic_region_handles_restored=true\nregion_ids_retained=true\n"
                + "pre_step_checkpoint_preserved=true\nduplicate_resume_rejected=true\n"
                + "ambiguous_singular_calls_rejected=true\nunknown_uuid_isolated=true\nresumed_rebased_bounds=true\n");
        server.tickRateManager().setFrozen(false); frozen = false;
        next(Phase.RESUME_A);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.complete || scenario.failed) return;
        scenario.guarded(scenario::advance);
    }

    private void advance() {
        require(++ticks < 1800 && ++age < 850, "Multi-region probe timed out at " + phase);
        switch (phase) {
            case ACTIVATE -> {
                if (a.lifecycle() != StructureLifecycle.ACTIVE || b.lifecycle() != StructureLifecycle.ACTIVE) return;
                if (STAGE.equals("prepare")) {
                    var chest = new ChestBlockEntity(CELL, Blocks.CHEST.defaultBlockState()); chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
                    put(a, CELL, new StructureBlockData(Blocks.CHEST.defaultBlockState(), chest.saveWithFullMetadata(host.registryAccess())));
                    put(a, STONE, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                    put(b, CELL, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                    a.setPose(poseAt(new Vec3(5.5, 115, 6.5), new Quaterniond().rotationXYZ(0.15, 0.2, 0.1)));
                    b.setPose(poseAt(new Vec3(23.5, 115, 6.5), new Quaterniond()));
                    regionA = StructurePhysics.openRegion(host, BOUNDS_A); regionB = StructurePhysics.openRegion(host, BOUNDS_B);
                    idA = regionA.id(); idB = regionB.id(); require(!idA.equals(idB), "Independent regions share their persistent UUID");
                    regionA.attach(a); regionB.attach(b); verifyRegionInfo(); verifyOpeningLimits(); next(Phase.INITIAL);
                }
            }
            case INITIAL -> {
                if (regionA.motion(a.reference()).isEmpty() || regionB.motion(b.reference()).isEmpty()) return;
                ready(regionA); ready(regionB); verifyWrongRegionCalls(); assertContents();
                require(!diagnostics(regionA).sessionId().equals(diagnostics(regionB).sessionId()), "Independent regions share a native session");
                stepsA = diagnostics(regionA).steps(); stepsB = diagnostics(regionB).steps(); poseB = b.pose();
                unloaded = a; oldRuntime = a.runtimeId();
                require(runtime.unload(a.reference()), "Region A member failed to unload"); next(Phase.PARKED);
            }
            case PARKED -> {
                require(regionA.status() == StructurePhysicsRegion.Status.PAUSED && regionA.diagnostic().equals("awaiting_structure_activation"),
                        "Unloaded region A did not pause independently: " + regionA.diagnostic());
                require(diagnostics(regionA).steps() == stepsA && regionA.motion(unloaded.reference()).isEmpty(), "Unloaded region A kept stepping");
                ready(regionB); require(diagnostics(regionB).steps() >= stepsB + age, "Region A pause blocked region B's native steps");
                if (age < 20) return;
                require(center(b).distanceTo(poseB.localToWorldPoint(CENTER)) > 0.5, "Region B did not physically continue during A's pause");
                expectStateRejected(() -> unloaded.pose(), "Old unloaded handle remained usable");
                a = runtime.load(unloaded.reference()).orElseThrow(); require(!a.runtimeId().equals(oldRuntime), "Reload reused an old structure runtime");
                report.append("unloaded_region_paused=true\nother_region_steps_and_pose_continued=true\nold_structure_handle_rejected=true\n");
                next(Phase.RELOADED);
            }
            case RELOADED -> {
                if (a.lifecycle() != StructureLifecycle.ACTIVE || regionA.motion(a.reference()).isEmpty()) return;
                ready(regionA); ready(regionB); require(regionA.id().equals(idA) && diagnostics(regionA).steps() > stepsA, "Reload lost region A's identity or motion");
                verifyWrongRegionCalls(); assertContents(); next(Phase.SETTLE);
            }
            case SETTLE -> {
                ready(regionA); ready(regionB);
                if (!regionA.motion(a.reference()).orElseThrow().sleeping() || !regionB.motion(b.reference()).orElseThrow().sleeping()) { sleepingTicks = 0; return; }
                if (++sleepingTicks < 20) return;
                // A resting contact can change the next velocity through collision response. Launch
                // vertically first, then measure checkpoint restoration only after both shapes clear it.
                setAndObserveVelocity(a, regionA, new Vec3(0, 6, 0), Vec3.ZERO, "lift_a");
                setAndObserveVelocity(b, regionB, new Vec3(0, 6, 0), Vec3.ZERO, "lift_b");
                next(Phase.MIGRATE);
            }
            case MIGRATE -> {
                ready(regionA); ready(regionB);
                var nativeRegionA = (NativeStructurePhysics.Region) regionA;
                var original = regionA.bounds();
                require(!nativeRegionA.rebind(BOUNDS_B), "A rebase overlapping the other region was accepted");
                require(regionA.bounds().equals(original) && regionA.status() == StructurePhysicsRegion.Status.READY,
                        "A rejected rebase changed the live region");
                var migrated = BOUNDS_A_MIGRATED;
                verifyFailedRebindPreservesWorld(migrated);
                migrationPose = a.pose();
                require(nativeRegionA.rebind(migrated), "A separated rebase was refused");
                require(regionA.bounds().equals(migrated) && regionA.diagnostic().equals("awaiting_rebound_geometry"),
                        "The live region did not adopt its rebased bounds");
                require(StructurePhysics.savedRegions(host).stream().filter(info -> info.id().equals(idA))
                                .allMatch(info -> info.bounds().equals(migrated)),
                        "Rebased bounds were not persisted before adoption");
                require(regionB.bounds().equals(BOUNDS_B) && regionB.status() == StructurePhysicsRegion.Status.READY,
                        "A region rebase disturbed the other region");
                report.append("region_rebase_conflict_rejected=true\nregion_rebase_adopted_and_persisted=true\n");
                stepsA = diagnostics(regionA).steps();
                next(Phase.AFTER_MIGRATE);
            }
            case AFTER_MIGRATE -> {
                if (regionA.status() != StructurePhysicsRegion.Status.READY || regionA.motion(a.reference()).isEmpty()) {
                    require(age < 250, "Region A did not resume after its rebase: " + regionA.diagnostic());
                    return;
                }
                ready(regionB);
                require(diagnostics(regionA).steps() > stepsA, "The rebased region did not step again");
                require(a.pose().worldOrigin().distanceTo(migrationPose.worldOrigin()) < 0.5,
                        "A region rebase teleported its member instead of preserving the pose");
                var infoA = StructurePhysics.savedRegions(host).stream().filter(info -> info.id().equals(idA))
                        .findFirst().orElseThrow();
                require(infoA.bounds().equals(BOUNDS_A_MIGRATED) && infoA.structures() == 1 && infoA.resumeAllowed(),
                        "The rebased region summary lost membership or bounds");
                assertContents();
                report.append("region_rebase_resumed=true\nregion_rebase_pose_preserved=true\n");
                next(Phase.LIFT);
            }
            case LIFT -> {
                ready(regionA); ready(regionB);
                require(age <= 20, "Bodies did not clear the platform: " + motionDiagnostic(a, regionA) + " / " + motionDiagnostic(b, regionB));
                if (minimumY(a) <= 111.35 || minimumY(b) <= 111.35) return;
                report.append("checkpoint_shapes_clear_platform=true\na_min_y=").append(minimumY(a)).append("\nb_min_y=").append(minimumY(b)).append('\n');
                setAndObserveVelocity(a, regionA, new Vec3(0.6, 3, 0.1), new Vec3(0.15, 0.4, 0.1), "checkpoint_a");
                setAndObserveVelocity(b, regionB, new Vec3(-0.6, 3, 0.1), new Vec3(0.1, -0.35, 0.15), "checkpoint_b");
                next(Phase.KICK);
            }
            case KICK -> {
                for (var pair : List.of(new Body(a, regionA), new Body(b, regionB))) {
                    var motion = pair.region().motion(pair.handle().reference()).orElseThrow();
                    var diagnostic = motionDiagnostic(pair.handle(), pair.region());
                    report.append("checkpoint_after_step=").append(diagnostic).append('\n');
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_REGIONS_CHECKPOINT_AFTER_STEP {}", diagnostic);
                    require(Math.abs(motion.linearVelocity().x) > 0.4 && motion.linearVelocity().y > 1 && motion.angularVelocity().length() > 0.2,
                            "Checkpoint requires actual nonzero motion in both regions: " + diagnostic);
                    require(minimumY(pair.handle()) > 111.35 && Math.abs(motion.linearVelocity().x - (pair.handle() == a ? 0.6 : -0.6)) < 1.0E-6
                                    && Math.abs(motion.linearVelocity().y - (3 - 9.81 * 0.05)) < 1.0E-6
                                    && Math.abs(motion.linearVelocity().z - 0.1) < 1.0E-6,
                            "Airborne checkpoint changed velocity beyond its real gravity step: " + diagnostic);
                    assertSavedMatchesActual(pair.handle(), pair.region(), motion);
                }
                server.tickRateManager().setFrozen(true); frozen = true;
                require(runtime.save(a.reference()) && runtime.save(b.reference()), "Multi-region checkpoint save failed");
                oracle = new CompoundTag(); oracle.putInt("schema", 1); oracle.putLong("pid", PID); oracle.putLong("process_start", PROCESS_START);
                oracle.put("a", writeBody(a, idA)); oracle.put("b", writeBody(b, idB)); writeNbt(oracle);
                report.append("region_a=").append(idA).append("\nregion_b=").append(idB).append("\nboth_checkpoint_velocities_nonzero=true\n");
                next(Phase.SAVED);
            }
            case SAVED -> { assertSaved(a, idA, "a"); assertSaved(b, idB, "b"); assertContents(); finish(); }
            case RESUME_A -> {
                assertSaved(b, idB, "b");
                if (regionA.motion(a.reference()).isEmpty()) return;
                assertFirstStep(a, regionA, "a"); stepsA = diagnostics(regionA).steps(); poseA = a.pose(); next(Phase.A_ONLY);
            }
            case A_ONLY -> {
                ready(regionA); assertSaved(b, idB, "b"); assertChest();
                require(regionB.status() == StructurePhysicsRegion.Status.PAUSED
                                && regionB.diagnostic().equals("saved_geometry_mismatch")
                                && diagnostics(regionB).steps() == 0 && regionB.motion(b.reference()).isEmpty(),
                        "Automatically restored B did not pause on its saved geometry mismatch");
                if (age < 5) return;
                require(diagnostics(regionA).steps() >= stepsA + 5 && center(a).distanceTo(poseA.localToWorldPoint(CENTER)) > 0.05,
                        "Automatically restored A did not advance while B remained paused");
                require(regionA.motion(b.reference()).isEmpty(), "Automatically restored A exposed region B's structure");
                report.append("automatic_resume_a_keeps_mismatched_b_paused=true\n");
                stepsA = diagnostics(regionA).steps(); poseA = a.pose(); next(Phase.MISMATCH_B);
            }
            case MISMATCH_B -> {
                ready(regionA); require(diagnostics(regionA).steps() >= stepsA + age, "Region B's mismatch blocked region A");
                require(regionB.status() == StructurePhysicsRegion.Status.PAUSED && regionB.diagnostic().equals("saved_geometry_mismatch")
                        && diagnostics(regionB).steps() == 0 && regionB.motion(b.reference()).isEmpty(), "Mismatched region B published motion");
                assertSaved(b, idB, "b"); assertChest();
                if (age < 5) return;
                require(center(a).distanceTo(poseA.localToWorldPoint(CENTER)) > 0.03, "Region A did not physically move during B's mismatch");
                put(b, CELL, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                report.append("mismatched_b_paused=true\na_steps_and_pose_continue=true\nb_checkpoint_retained=true\n"); next(Phase.RESUME_B);
            }
            case RESUME_B -> {
                ready(regionA);
                if (regionB.motion(b.reference()).isEmpty()) return;
                assertFirstStep(b, regionB, "b"); verifyWrongRegionCalls(); assertContents();
                stepsB = diagnostics(regionB).steps(); poseB = b.pose(); next(Phase.BOTH);
            }
            case BOTH -> {
                ready(regionA); ready(regionB); if (age < 5) return;
                require(diagnostics(regionB).steps() >= stepsB + 5 && center(b).distanceTo(poseB.localToWorldPoint(CENTER)) > 0.05,
                        "Region B did not resume real movement after geometry repair");
                var beforeB = runtime.savedPhysicsEntries(host, idB);
                regionA.close(); require(regionA.status() == StructurePhysicsRegion.Status.CLOSED, "Region A did not close");
                require(runtime.savedPhysicsEntries(host, idA).isEmpty() && runtime.savedPhysicsEntries(host, idB).equals(beforeB),
                        "Closing region A removed or changed region B's checkpoint");
                expectStateRejected(() -> regionA.setVelocity(a.reference(), Vec3.ZERO, Vec3.ZERO), "Closed region handle accepted control");
                regionA.close(); ready(regionB);
                require(runtime.savedPhysicsEntries(host, idB).equals(beforeB), "Repeated stale region close affected live region B");
                stepsB = diagnostics(regionB).steps(); poseB = b.pose(); next(Phase.CLOSE_A);
            }
            case CLOSE_A -> {
                ready(regionB); require(runtime.savedPhysicsEntries(host, idA).isEmpty(), "Closed region A reappeared");
                if (age < 5) return;
                require(diagnostics(regionB).steps() >= stepsB + 5 && center(b).distanceTo(poseB.localToWorldPoint(CENTER)) > 0.03,
                        "Closing region A stopped region B's actual motion");
                require(StructurePhysics.savedRegions(host).size() == 1 && StructurePhysics.savedRegions(host).getFirst().id().equals(idB),
                        "Closing A did not preserve exactly B's persistent identity");
                regionB.close(); require(StructurePhysics.savedRegions(host).isEmpty(), "Closing B retained a saved attachment");
                require(!StructurePhysics.discardSavedRegion(host, idA) && !StructurePhysics.discardSavedRegion(host, idB), "Cleared UUID still identified saved intent");
                require(runtime.save(a.reference()) && runtime.save(b.reference()), "Saving cleared region intent failed");
                report.append("close_a_only_clears_a=true\nb_motion_continues_after_a_close=true\nclosed_region_handle_rejected=true\nclose_b_clears_remaining=true\n");
                next(Phase.CLEAR);
            }
            case CLEAR -> { require(StructurePhysics.savedRegions(host).isEmpty(), "Save recreated cleared regions"); assertContents(); finish(); }
            case FINISHED -> { }
        }
    }

    private void verifyOpeningLimits() {
        var before = runtime.savedPhysicsEntries(host);
        expectRejected(() -> StructurePhysics.openRegion(host, new AABB(-17, 108, -2, -1, 128, 14)), "Overlapping region was accepted");
        expectRejected(() -> StructurePhysics.openRegion(host, new AABB(-18, 108, -2, -2, 128, 14)), "Touching region was accepted");
        expectRejected(() -> StructurePhysics.openRegion(host, new AABB(-18.5, 108, -2, -2.5, 128, 14)), "Sub-block region gap was accepted");
        var thirdBounds = new AABB(-19, 108, -2, -3, 128, 14);
        var third = StructurePhysics.openRegion(host, thirdBounds);
        require(!third.id().equals(idA) && !third.id().equals(idB), "Third region reused a live UUID");
        expectStateRejected(() -> StructurePhysics.openRegion(host, new AABB(64, 108, -2, 80, 128, 14)), "Fourth region exceeded the global region budget");
        var oldId = third.id(); third.close();
        var replacement = StructurePhysics.openRegion(host, thirdBounds);
        require(!replacement.id().equals(oldId), "A new region reused the closed region's UUID");
        third.close(); require(replacement.status() == StructurePhysicsRegion.Status.READY, "Old empty region close removed its replacement");
        expectStateRejected(() -> third.attach(a), "Closed region handle accepted a structure"); replacement.close();
        require(runtime.savedPhysicsEntries(host).equals(before), "Opening validation changed existing attachments");
        report.append("overlap_touch_small_gap_rejected=true\nexact_one_block_gap_accepted=true\nthree_region_limit_enforced=true\nclosed_uuid_not_reused=true\n");
    }

    private void verifyWrongRegionCalls() {
        var before = runtime.savedPhysicsEntries(host); var beforeA = a.pose(); var beforeB = b.pose();
        require(regionA.motion(b.reference()).isEmpty() && regionB.motion(a.reference()).isEmpty(), "Wrong region exposed another region's motion");
        require(!regionA.detach(b.reference()) && !regionB.detach(a.reference()), "Wrong region detached another region's member");
        expectRejected(() -> regionA.setVelocity(b.reference(), Vec3.ZERO, Vec3.ZERO), "Wrong region controlled another region's member");
        expectRejected(() -> regionB.applyImpulse(a.reference(), new Vec3(1, 0, 0), CENTER), "Wrong region applied an impulse");
        expectRejected(() -> regionA.setControl(b.reference(), new StructurePhysicsControl.Force(Vec3.ZERO, Vec3.ZERO)),
                "Wrong region installed a transient controller");
        expectRejected(() -> regionA.acquireControl(b.reference(), UUID.randomUUID(), new StructurePhysicsControl.Force(CENTER, Vec3.ZERO), 1),
                "Wrong region acquired another region's member");
        expectRejected(() -> regionB.acquireControl(a.reference(), UUID.randomUUID(), new StructurePhysicsControl.Force(CENTER, Vec3.ZERO), 1),
                "Reverse wrong-region lease acquisition succeeded");
        require(regionA.control(b.reference()).isEmpty() && regionB.control(a.reference()).isEmpty(),
                "Wrong region exposed another region's controller");
        require(!regionA.clearControl(b.reference()) && !regionB.clearControl(a.reference()),
                "Wrong region cleared another region's controller");
        expectRejected(() -> regionA.attach(b), "One structure became attached to two regions");
        require(runtime.savedPhysicsEntries(host).equals(before) && samePose(a.pose(), beforeA) && samePose(b.pose(), beforeB),
                "Rejected cross-region operation mutated motion or pose");
        assertContents(); report.append("wrong_region_operations_isolated=true\nwrong_region_controls_isolated=true\nwrong_region_leases_isolated=true\n");
    }

    private void verifyRegionInfo() {
        var values = StructurePhysics.savedRegions(host); require(values.size() == 2 && !idA.equals(idB), "Expected exactly two distinct saved regions");
        // The resume stage reads the checkpoint written after region A's verified rebase.
        var expectedA = STAGE.equals("resume") ? BOUNDS_A_MIGRATED : BOUNDS_A;
        for (var pair : List.of(new RegionBounds(idA, expectedA), new RegionBounds(idB, BOUNDS_B))) {
            var info = values.stream().filter(value -> value.id().equals(pair.id())).findFirst().orElseThrow();
            require(info.bounds().equals(pair.bounds()) && info.structures() == 1 && info.resumeAllowed(), "Saved region summary changed membership or bounds");
            require(runtime.savedPhysicsEntries(host, pair.id()).size() == 1, "UUID-scoped manifest lookup crossed region membership");
        }
    }

    private void assertFirstStep(StructureHandle handle, StructurePhysicsRegion region, String key) {
        ready(region); var motion = region.motion(handle.reference()).orElseThrow();
        var saved = oracle.getCompoundOrEmpty(key).getCompoundOrEmpty("motion");
        var linear = readVector(saved.getCompoundOrEmpty("linear")); var angular = readVector(saved.getCompoundOrEmpty("angular"));
        require(!diagnostics(region).sessionId().toString().equals(oracle.getCompoundOrEmpty(key).getStringOr("session", "")),
                "A new JVM reused a native region session identity");
        require(diagnostics(region).steps() == 1, "Recovery observation missed the first native step");
        require(Math.abs(motion.linearVelocity().x - linear.x) < 0.1 && Math.abs(motion.linearVelocity().z - linear.z) < 0.1
                        && motion.linearVelocity().y > 1 && motion.linearVelocity().y < linear.y && motion.angularVelocity().distanceTo(angular) < 0.15,
                "Recovered region lost saved velocity: " + key + " saved=" + linear + "/" + angular + " actual=" + motion.linearVelocity() + "/" + motion.angularVelocity());
        report.append(key).append("_first_step_velocity_restored=true\n");
    }

    private void assertSaved(StructureHandle handle, UUID id, String key) {
        var saved = oracle.getCompoundOrEmpty(key); var entry = entry(handle, id);
        require(samePose(handle.pose(), readPose(saved.getCompoundOrEmpty("pose"))) && samePose(entry.pose(), handle.pose()), "Pending region advanced its saved pose");
        require(entry.slot() == saved.getIntOr("slot", -1) && entry.physicsAttachment().regionId().equals(id)
                        && entry.physicsAttachment().resumeAllowed() && writeMotion(entry.physicsAttachment().motion()).equals(saved.getCompoundOrEmpty("motion")),
                "Pending region changed persistent identity, slot, motion or geometry digest");
    }

    private void assertSavedMatchesActual(StructureHandle handle, StructurePhysicsRegion region, StructureMotionState actual) {
        var entry = entry(handle, region.id()); var motion = entry.physicsAttachment().motion();
        require(motion != null && samePose(entry.pose(), actual.pose())
                        && motion.linearVelocity().distanceToSqr(actual.linearVelocity()) < 1.0E-18
                        && motion.angularVelocity().distanceToSqr(actual.angularVelocity()) < 1.0E-18
                        && motion.localCenterOfMass().distanceToSqr(actual.localCenterOfMass()) < 1.0E-18 && motion.sleeping() == actual.sleeping(),
                "Saved checkpoint differs from the coherent native observation");
    }

    private void setAndObserveVelocity(StructureHandle handle, StructurePhysicsRegion region, Vec3 linear, Vec3 angular, String label) {
        region.setVelocity(handle.reference(), linear, angular);
        var actual = region.motion(handle.reference()).orElseThrow();
        var diagnostic = motionDiagnostic(handle, region);
        report.append(label).append("_control=").append(diagnostic).append('\n');
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_REGIONS_CONTROL label={} {}", label, diagnostic);
        require(actual.linearVelocity().distanceToSqr(linear) < 1.0E-18 && actual.angularVelocity().distanceToSqr(angular) < 1.0E-18,
                "Velocity control did not immediately publish requested values: " + label + " " + diagnostic);
    }

    private String motionDiagnostic(StructureHandle handle, StructurePhysicsRegion region) {
        var motion = region.motion(handle.reference()).orElseThrow();
        return "region=" + region.id() + " ref=" + handle.reference().id() + " steps=" + diagnostics(region).steps()
                + " status=" + region.status() + " linear=" + motion.linearVelocity() + " angular=" + motion.angularVelocity()
                + " sleeping=" + motion.sleeping() + " shape_min_y=" + minimumY(handle);
    }

    private double minimumY(StructureHandle handle) {
        var bounds = NativeStructurePhysicsGeometry.captureStructure(storage(handle)).bounds();
        double result = Double.POSITIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            result = Math.min(result, handle.pose().localToWorldPoint(new Vec3(x == 0 ? bounds.minX : bounds.maxX,
                    y == 0 ? bounds.minY : bounds.maxY, z == 0 ? bounds.minZ : bounds.maxZ)).y);
        }
        return result;
    }

    private void assertContents() {
        require(storage(a).players().isEmpty() && storage(b).players().isEmpty(), "Structure storage acquired a player");
        require(a.world().read(STONE).state().is(Blocks.STONE) && b.world().read(CELL).state().is(Blocks.STONE), "Stored fixture blocks changed"); assertChest();
    }

    private void assertChest() {
        require(storage(a).getBlockEntity(CELL) instanceof ChestBlockEntity, "Real chest block entity missing");
        var chest = (ChestBlockEntity) storage(a).getBlockEntity(CELL);
        require(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7, "Chest inventory was lost or duplicated");
        for (int slot = 1; slot < chest.getContainerSize(); slot++) require(chest.getItem(slot).isEmpty(), "Unexpected fixture chest contents");
        if (oracle != null) require(a.world().read(CELL).blockEntityData().equals(oracle.getCompoundOrEmpty("a").getCompoundOrEmpty("chest")), "Complete chest NBT changed");
    }

    private NativeStructureManifest.Entry entry(StructureHandle handle, UUID id) { return runtime.savedPhysicsEntries(host, id).stream().filter(value -> value.reference().equals(handle.reference())).findFirst().orElseThrow(); }
    private CompoundTag writeBody(StructureHandle handle, UUID id) {
        var tag = new CompoundTag(); var ref = handle.reference(); var entry = entry(handle, id);
        tag.putString("id", ref.id().toString()); tag.putString("dimension", ref.dimension().toString()); tag.putString("backend", ref.backend().toString()); tag.putLong("generation", ref.generation());
        tag.putString("region", id.toString()); tag.putString("runtime", handle.runtimeId().toString()); tag.putInt("slot", entry.slot());
        tag.putString("session", diagnostics(handle == a ? regionA : regionB).sessionId().toString());
        tag.put("pose", writePose(handle.pose())); tag.put("motion", writeMotion(entry.physicsAttachment().motion()));
        tag.put("bounds", writeBounds(entry.physicsAttachment().bounds()));
        if (handle == a) tag.put("chest", handle.world().read(CELL).blockEntityData()); return tag;
    }

    private void next(Phase next) { phase = next; age = 0; AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_REGIONS_PHASE stage={} phase={} ticks={}", STAGE, phase, ticks); }
    private void releaseTickets() { while (!tickets.isEmpty()) { var chunk = tickets.removeFirst(); host.setChunkForced(chunk.x(), chunk.z(), false); } }
    private void finish() {
        require(!complete && !failed, "Probe already finished"); releaseTickets();
        report.append("fixture_tickets_released=true\nticks=").append(ticks).append('\n'); writeText(resultName(STAGE), "PASSED\n" + report);
        complete = true; next(Phase.FINISHED); AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_REGIONS_PASSED stage={} pid={}", STAGE, PID); server.halt(false);
    }
    private void guarded(Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            failed = true; AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_REGIONS_FAILED stage={} phase={}", STAGE, phase, error);
            try { releaseTickets(); } catch (Throwable release) { error.addSuppressed(release); }
            try { writeText(resultName(STAGE), "FAILED\nphase=" + phase + "\nfailure=" + error + "\n" + report); } catch (Throwable writing) { error.addSuppressed(writing); }
            // Failure must not clear associations by closing either public region handle.
            server.halt(false);
        }
    }
    /** Real manifest write refusal, confined to the verified disposable prepare-world directory. */
    private void verifyFailedRebindPreservesWorld(AABB proposed) {
        require(STAGE.equals("prepare") && runtime.save(a.reference()), "Cannot checkpoint rebase refusal fixture");
        var metadata = server.getWorldPath(LevelResource.ROOT).resolve("data/academy/native_structures/metadata.dat");
        var backup = metadata.resolveSibling("metadata.rebind-smoke-backup");
        var blocker = metadata.resolve("deny-replace");
        require(Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(backup, LinkOption.NOFOLLOW_LINKS), "Unexpected rebase fixture metadata paths");
        var before = diagnostics(regionA);
        var motion = regionA.motion(a.reference()).orElseThrow();
        var checkpoint = runtime.savedPhysicsEntries(host);
        var bounds = regionA.bounds();
        boolean moved = false, blocked = false;
        try {
            Files.move(metadata, backup);
            moved = true;
            Files.createDirectory(metadata);
            blocked = true;
            Files.writeString(blocker, "Intentional bounded rebase write refusal", StandardOpenOption.CREATE_NEW);
            require(!regionA.rebind(proposed), "Manifest write refusal accepted a rebase");
            require(regionA.bounds().equals(bounds) && regionA.status() == StructurePhysicsRegion.Status.READY
                            && diagnostics(regionA).equals(before), "Failed persistence destroyed the previous native world");
            require(regionA.motion(a.reference()).orElseThrow().equals(motion)
                            && runtime.savedPhysicsEntries(host).equals(checkpoint), "Failed rebase changed motion or attachments");
            // This requires live unpaused bodies; a stale published snapshot alone cannot pass.
            regionA.setVelocity(a.reference(), motion.linearVelocity(), motion.angularVelocity());
            report.append("region_rebase_io_failure_keeps_world=true\nregion_rebase_io_failure_keeps_motion=true\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot exercise failed physics rebase", failure);
        } finally {
            try {
                if (blocked) {
                    Files.deleteIfExists(blocker);
                    Files.delete(metadata);
                }
                if (moved) Files.move(backup, metadata);
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot restore rebase fixture metadata; backup retained at " + backup, failure);
            }
        }
    }

    private void writeText(String name, String text) { try { Files.writeString(directory.resolve(name), text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); } catch (IOException error) { throw new IllegalStateException("Cannot write immutable region probe result", error); } }
    private void writeNbt(CompoundTag tag) { try (var output = Files.newOutputStream(directory.resolve("regions-checkpoint.nbt"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { NbtIo.writeCompressed(tag, output); } catch (IOException error) { throw new IllegalStateException("Cannot write region checkpoint evidence", error); } }

    private static Path verifyDirectory(MinecraftServer server) {
        try {
            var path = Path.of(System.getProperty("academy.nativeStructurePhysicsRegionsDirectory")).toRealPath();
            var root = Path.of(System.getProperty("academy.nativeStructurePhysicsRegionsRunRoot")).toRealPath();
            require(path.startsWith(root) && path.getFileName().toString().startsWith("native-structure-physics-regions-"), "Multi-region probe requires an isolated run directory");
            require(server.getServerDirectory().toRealPath().equals(path) && server.getWorldPath(LevelResource.ROOT).toRealPath().equals(path.resolve(WORLD)), "Refusing linked or unrelated multi-region world");
            require(!Files.exists(path.resolve(resultName(STAGE)), LinkOption.NOFOLLOW_LINKS) && !Files.exists(path.resolve(exitName(STAGE)), LinkOption.NOFOLLOW_LINKS), "Probe stage has already run");
            if (STAGE.equals("prepare")) require(!Files.exists(path.resolve("regions-checkpoint.nbt"), LinkOption.NOFOLLOW_LINKS), "Prepare needs a fresh checkpoint path");
            else require(Files.readString(path.resolve(resultName("prepare"))).startsWith("PASSED\n")
                            && Files.readString(path.resolve(exitName("prepare"))).contains("exit_status=0\n")
                            && Files.readString(path.resolve(exitName("prepare"))).contains("stopping_guards_verified=true\n"), "Prepare JVM did not save and stop normally");
            return path;
        } catch (IOException error) { throw new IllegalStateException("Cannot verify multi-region probe isolation", error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var scenario = active; if (scenario == null || scenario.server != event.getServer()) return; scenario.stopping = true;
        try { scenario.verifyStoppingGuards(); }
        catch (Throwable error) { scenario.failed = true; AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_REGIONS_STOPPING_GUARDS_FAILED stage={}", STAGE, error); }
        try { scenario.releaseTickets(); if (scenario.frozen) { scenario.server.tickRateManager().setFrozen(false); scenario.frozen = false; } }
        catch (Throwable error) { scenario.failed = true; AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_REGIONS_STOPPING_CLEANUP_FAILED stage={}", STAGE, error); }
    }
    private void verifyStoppingGuards() {
        require(complete && !failed && oracle != null, "Stopping checks need a completed scenario"); var saved = runtime.savedPhysicsEntries(host);
        var ref = readRef(oracle.getCompoundOrEmpty("a")); var pose = readPose(oracle.getCompoundOrEmpty("a").getCompoundOrEmpty("pose"));
        expectStateRejected(() -> runtime.load(ref), "Stopping backend allowed load"); expectStateRejected(() -> runtime.createEmpty(host, pose), "Stopping backend allocated another owner");
        expectStateRejected(() -> StructurePhysics.openRegion(host, BOUNDS_A), "Stopping backend opened a region");
        expectStateRejected(() -> StructurePhysics.resumeRegion(host, idA), "Stopping backend resumed a UUID");
        expectStateRejected(() -> StructurePhysics.discardSavedRegion(host, idB), "Stopping backend discarded a UUID");
        boolean savedAgain = runtime.save(ref), unloadedAgain = runtime.unload(ref);
        require(!savedAgain && !unloadedAgain && runtime.savedPhysicsEntries(host).equals(saved), "Stopping callbacks changed saved ownership or checkpoints");
        stoppingGuardsVerified = true; AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_REGIONS_STOPPING_GUARDS_PASSED stage={}", STAGE);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var scenario = active; if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.watching) return; scenario.watching = true;
        Thread.ofPlatform().daemon().name("academy-physics-regions-exit").start(() -> {
            try { scenario.server.getRunningThread().join(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            int status = scenario.complete && !scenario.failed && scenario.stopping && scenario.stoppingGuardsVerified && !scenario.server.isCurrentlySaving() ? 0 : 1;
            try {
                var crashDirectory = scenario.directory.resolve("crash-reports");
                if (Files.isDirectory(crashDirectory)) try (var files = Files.list(crashDirectory)) { if (files.findAny().isPresent()) status = 1; }
                scenario.writeText(exitName(STAGE), "exit_status=" + status + "\nserver_stopped_event=true\nserver_thread_terminated=true\nstopping_guards_verified=" + scenario.stoppingGuardsVerified + "\n");
            } catch (Throwable error) { status = 1; }
            System.out.println("NATIVE_STRUCTURE_PHYSICS_REGIONS_EXIT stage=" + STAGE + " status=" + status); ServerLifecycleHooks.handleExit(status);
        });
    }

    private record Body(StructureHandle handle, StructurePhysicsRegion region) {}
    private record RegionBounds(UUID id, AABB bounds) {}
    private static String resultName(String stage) { return "native-physics-regions-" + stage + "-result.txt"; }
    private static String exitName(String stage) { return "native-physics-regions-" + stage + "-exit.txt"; }
    private ServerLevel storage(StructureHandle handle) { return runtime.storageLevel(handle.reference()).orElseThrow(); }
    private static NativeStructurePhysics.Diagnostics diagnostics(StructurePhysicsRegion region) { return ((NativeStructurePhysics.Region) region).diagnostics(); }
    private static void ready(StructurePhysicsRegion region) { require(region.status() == StructurePhysicsRegion.Status.READY, "Unexpected region state: " + region.id() + " " + region.status() + " " + region.diagnostic()); }
    private static Vec3 center(StructureHandle handle) { return handle.pose().localToWorldPoint(CENTER); }
    private static void put(StructureHandle handle, BlockPos pos, StructureBlockData data) { require(handle.world().compareAndSet(pos, handle.world().read(pos), data) == StructureWriteResult.APPLIED, "Fixture block write failed"); }
    private static void expectRejected(Runnable action, String message) { boolean rejected = false; try { action.run(); } catch (IllegalStateException | IllegalArgumentException expected) { rejected = true; } require(rejected, message); }
    private static void expectStateRejected(Runnable action, String message) { boolean rejected = false; try { action.run(); } catch (IllegalStateException expected) { rejected = true; } require(rejected, message); }
    private static CompoundTag writeMotion(NativeStructurePhysicsAttachment.Motion motion) { require(motion != null, "No coherent saved motion"); var tag = new CompoundTag(); tag.put("linear", writeVector(motion.linearVelocity())); tag.put("angular", writeVector(motion.angularVelocity())); tag.put("com", writeVector(motion.localCenterOfMass())); tag.putBoolean("sleeping", motion.sleeping()); tag.putString("digest", motion.geometryDigest()); return tag; }
    private static CompoundTag writeVector(Vec3 value) { var tag = new CompoundTag(); tag.putDouble("x", value.x); tag.putDouble("y", value.y); tag.putDouble("z", value.z); return tag; }
    private static Vec3 readVector(CompoundTag tag) { return new Vec3(tag.getDoubleOr("x", Double.NaN), tag.getDoubleOr("y", Double.NaN), tag.getDoubleOr("z", Double.NaN)); }
    private static CompoundTag writeBounds(AABB bounds) { var tag = writeVector(bounds.getMinPosition()); tag.put("max", writeVector(bounds.getMaxPosition())); return tag; }
    private static AABB readBounds(CompoundTag tag) { return new AABB(readVector(tag), readVector(tag.getCompoundOrEmpty("max"))); }
    private static CompoundTag writePose(StructurePose pose) { var tag = writeVector(pose.worldOrigin()); tag.putDouble("qx", pose.rotationX()); tag.putDouble("qy", pose.rotationY()); tag.putDouble("qz", pose.rotationZ()); tag.putDouble("qw", pose.rotationW()); return tag; }
    private static StructurePose readPose(CompoundTag tag) { return new StructurePose(readVector(tag), tag.getDoubleOr("qx", Double.NaN), tag.getDoubleOr("qy", Double.NaN), tag.getDoubleOr("qz", Double.NaN), tag.getDoubleOr("qw", Double.NaN)); }
    private static StructureRef readRef(CompoundTag tag) { return new StructureRef(UUID.fromString(tag.getStringOr("id", "")), Identifier.parse(tag.getStringOr("dimension", "")), Identifier.parse(tag.getStringOr("backend", "")), tag.getLongOr("generation", -1)); }
    private static StructurePose poseAt(Vec3 center, Quaterniond rotation) { var basis = new StructurePose(Vec3.ZERO, rotation); return new StructurePose(center.subtract(basis.localToWorldDirection(CENTER)), rotation); }
    private static boolean samePose(StructurePose a, StructurePose b) { return a.worldOrigin().distanceToSqr(b.worldOrigin()) < 1.0E-18 && 1 - Math.abs(a.rotationX() * b.rotationX() + a.rotationY() * b.rotationY() + a.rotationZ() * b.rotationZ() + a.rotationW() * b.rotationW()) < 1.0E-14; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
