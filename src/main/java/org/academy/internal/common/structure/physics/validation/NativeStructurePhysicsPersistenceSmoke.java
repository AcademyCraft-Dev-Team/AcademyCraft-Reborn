package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Three real JVMs, one disposable world: checkpoint, unavailable engine, explicit recovery. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructurePhysicsPersistenceSmoke {
    public static final String WORLD = "native-structure-physics-persistence-smoke";
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructurePhysicsPersistenceSmoke");
    private static final String MODE = System.getProperty("academy.nativeStructurePhysicsPersistenceStage", "");
    private static final BlockPos CELL = new BlockPos(0, 80, 0);
    private static final BlockPos STONE = CELL.east();
    private static final Vec3 CENTER = Vec3.atCenterOf(CELL);
    private static final AABB BOUNDS = new AABB(-2, 108, -2, 18, 132, 18);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final long PID = ProcessHandle.current().pid();
    private static final long PROCESS_START = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static NativeStructurePhysicsPersistenceSmoke active;
    private enum Phase { ACTIVATE, SETTLE, KICK, CHECKPOINT, UNAVAILABLE, MISMATCH, RESUME, MOVING, PARKED, RELOADED, CLEAR, FINISHED }

    private final MinecraftServer server;
    private final ServerLevel host;
    private final NativeStructureRuntime runtime;
    private final Path directory;
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final StringBuilder report = new StringBuilder("normal_shutdown_only=true\nautomatic_resume=false\n");
    private StructureHandle moving, resting, unloaded;
    private StructurePhysicsRegion region;
    private CompoundTag oracle;
    private Phase phase = Phase.ACTIVATE;
    private int ticks, age, sleepingTicks;
    private boolean complete, failed, frozen, stopping, stoppingGuardsVerified, watching;
    private long parkedSteps;
    private StructurePose parkedPose, resumedPose;
    private UUID unloadedRuntime;

    private NativeStructurePhysicsPersistenceSmoke(MinecraftServer server) {
        this.server = server;
        directory = verifyDirectory(server);
        host = server.overworld();
        runtime = NativeStructureRuntime.backend(server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (!ENABLED) return;
        require(List.of("prepare", "unavailable", "resume").contains(MODE), "Unknown persistence probe stage");
        require(active == null && event.getServer().isDedicatedServer(), "Persistence probe requires a dedicated server");
        // Directory verification happens before any fixture mutation or controlled process exit is armed.
        var scenario = new NativeStructurePhysicsPersistenceSmoke(event.getServer());
        active = scenario;
        scenario.guarded(scenario::initialize);
    }

    private void initialize() {
        report.append("stage=").append(MODE).append("\npid=").append(PID).append("\nprocess_start=").append(PROCESS_START).append('\n');
        if (MODE.equals("prepare")) {
            require(StructurePhysics.savedRegionBounds(host).isEmpty(), "Fresh world already has a saved physics region");
            forceHostChunks();
            for (var pos : BlockPos.betweenClosed(new BlockPos(-1, 109, -1), new BlockPos(17, 131, 17))) {
                require(host.getBlockState(pos).isAir(), "Refusing to replace existing fixture host content at " + pos);
            }
            for (var pos : BlockPos.betweenClosed(new BlockPos(3, 110, 3), new BlockPos(12, 110, 12))) {
                require(host.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS), "Cannot create isolated platform");
            }
            moving = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow();
            resting = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow();
        } else {
            try { oracle = NbtIo.readCompressed(directory.resolve("checkpoint.nbt"), NbtAccounter.create(262144)); }
            catch (IOException error) { throw new IllegalStateException("Cannot read preceding JVM's checkpoint evidence", error); }
            require(oracle.getIntOr("schema", -1) == 1, "Unexpected fixture evidence schema");
            requireDifferentProcess(oracle);
            if (MODE.equals("resume")) {
                try { requireDifferentProcess(NbtIo.readCompressed(directory.resolve("unavailable.nbt"), NbtAccounter.create(262144))); }
                catch (IOException error) { throw new IllegalStateException("Cannot read missing-engine JVM evidence", error); }
            }
            moving = runtime.resolve(readRef(oracle.getCompoundOrEmpty("moving"))).orElseThrow(() ->
                    new IllegalStateException("Saved ACTIVE moving structure was not activated on startup"));
            resting = runtime.resolve(readRef(oracle.getCompoundOrEmpty("resting"))).orElseThrow(() ->
                    new IllegalStateException("Saved ACTIVE resting structure was not activated on startup"));
            require(!moving.runtimeId().toString().equals(oracle.getCompoundOrEmpty("moving").getStringOr("runtime", ""))
                    && !resting.runtimeId().toString().equals(oracle.getCompoundOrEmpty("resting").getStringOr("runtime", "")),
                    "A new JVM reused an old structure runtime identity");
            report.append("different_jvm=true\nnew_runtime_ids=true\n");
            if (MODE.equals("resume")) forceHostChunks();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.complete || scenario.failed) return;
        scenario.guarded(scenario::advance);
    }

    private void advance() {
        require(++ticks < 1800 && ++age < 850, "Persistence probe timed out at " + phase);
        switch (phase) {
            case ACTIVATE -> {
                if (moving.lifecycle() != StructureLifecycle.ACTIVE || resting.lifecycle() != StructureLifecycle.ACTIVE) return;
                require(storage(moving).players().isEmpty() && storage(resting).players().isEmpty(), "Storage acquired a player");
                if (MODE.equals("prepare")) {
                    var chest = new ChestBlockEntity(CELL, Blocks.CHEST.defaultBlockState());
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
                    put(moving, CELL, new StructureBlockData(Blocks.CHEST.defaultBlockState(), chest.saveWithFullMetadata(host.registryAccess())));
                    put(moving, STONE, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                    put(resting, CELL, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                    moving.setPose(poseAt(new Vec3(5.5, 115, 7.5), new Quaterniond().rotationXYZ(0.18, 0.2, 0.15)));
                    resting.setPose(poseAt(new Vec3(10.5, 112, 7.5), new Quaterniond()));
                    region = StructurePhysics.openRegion(host, BOUNDS);
                    region.attach(moving); region.attach(resting);
                    next(Phase.SETTLE);
                } else {
                    assertCheckpoint();
                    if (MODE.equals("unavailable")) {
                        require(System.getProperty("academy.nativePhysics.library") == null
                                && System.getProperty("academy.nativePhysics.manifest") == null, "Unavailable stage must omit native artifact configuration");
                        expectRejected(() -> StructurePhysics.openRegion(host, BOUNDS), "Fresh region replaced pending recovery intent");
                        expectRejected(() -> moving.setPose(StructurePose.identity(new Vec3(1, 120, 1))), "Direct setPose replaced pending recovery pose");
                        assertCheckpoint();
                        report.append("pending_open_rejected=true\npending_set_pose_rejected=true\n");
                        boolean rejected = false;
                        try { StructurePhysics.resumeRegion(host); }
                        catch (IllegalStateException expected) { rejected = true; report.append("missing_engine_failure=").append(expected).append('\n'); }
                        require(rejected, "Missing engine configuration unexpectedly resumed physics");
                        assertCheckpoint();
                        next(Phase.UNAVAILABLE);
                    } else {
                        rejectOldHit();
                        put(moving, STONE, new StructureBlockData(Blocks.STONE_SLAB.defaultBlockState()));
                        region = StructurePhysics.resumeRegion(host);
                        require(sameBounds(region.bounds(), BOUNDS), "Explicit resume changed saved region bounds");
                        next(Phase.MISMATCH);
                    }
                }
            }
            case SETTLE -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED, "Solver failed: " + region.diagnostic());
                var a = region.motion(moving.reference()); var b = region.motion(resting.reference());
                if (a.isEmpty() || b.isEmpty() || !a.get().sleeping() || !b.get().sleeping()) { sleepingTicks = 0; return; }
                if (++sleepingTicks < 20) return;
                require(diagnostics().bodies() == 2, "Both structures must share the actual native world");
                region.setVelocity(moving.reference(), new Vec3(0.75, 3, 0.15), new Vec3(0.2, 0.4, 0.1));
                next(Phase.KICK);
            }
            case KICK -> {
                var motion = region.motion(moving.reference()).orElseThrow();
                require(motion.linearVelocity().x > 0.5 && motion.linearVelocity().y > 1
                        && motion.angularVelocity().length() > 0.2 && !motion.sleeping(), "Checkpoint needs actual nonzero linear and angular motion");
                require(region.motion(resting.reference()).orElseThrow().sleeping(), "Independent resting fixture must remain asleep");
                region.setControl(moving.reference(), new StructurePhysicsControl.Anchor(motion.localCenterOfMass(),
                        motion.pose().localToWorldPoint(motion.localCenterOfMass()).add(0, 0.5, 0), Vec3.ZERO, 60, 16, 40));
                region.setControl(resting.reference(), new StructurePhysicsControl.Force(
                        region.motion(resting.reference()).orElseThrow().localCenterOfMass(), Vec3.ZERO));
                server.tickRateManager().setFrozen(true); frozen = true;
                require(runtime.save(moving.reference()) && runtime.save(resting.reference()), "Checkpoint save failed");
                require(region.control(moving.reference()).isPresent() && region.control(resting.reference()).isPresent(),
                        "Control persistence probe must save while both transient configurations exist");
                report.append("transient_controls_present_at_save=true\n");
                assertSavedMotion(moving, motion);
                assertSavedMotion(resting, region.motion(resting.reference()).orElseThrow());
                oracle = new CompoundTag(); oracle.putInt("schema", 1); oracle.putLong("pid", PID); oracle.putLong("process_start", PROCESS_START);
                oracle.put("moving", writeBody(moving)); oracle.put("resting", writeBody(resting));
                saveHit(oracle);
                writeNbt("checkpoint.nbt", oracle);
                report.append("linear_velocity=").append(motion.linearVelocity()).append("\nangular_velocity=").append(motion.angularVelocity())
                        .append("\nsleep_observation_saved=true\nactive_at_shutdown=true\n");
                next(Phase.CHECKPOINT);
            }
            case CHECKPOINT -> {
                assertCheckpoint();
                require(region.control(moving.reference()).isEmpty() && region.control(resting.reference()).isEmpty(),
                        "Frozen save retained transient controls into another tick");
                report.append("control_freeze_does_not_replay=true\n");
                finish();
            }
            case UNAVAILABLE -> {
                assertCheckpoint();
                if (age < 40) return;
                require(runtime.save(moving.reference()) && runtime.save(resting.reference()), "Unavailable-engine save failed");
                assertCheckpoint();
                var evidence = new CompoundTag(); evidence.putLong("pid", PID); evidence.putLong("process_start", PROCESS_START);
                evidence.put("moving", writeBody(moving)); evidence.put("resting", writeBody(resting));
                writeNbt("unavailable.nbt", evidence);
                report.append("native_paths_absent=true\nfailed_resume_retained_checkpoint=true\nno_offline_steps=true\n");
                finish();
            }
            case MISMATCH -> {
                require(region.control(moving.reference()).isEmpty() && region.control(resting.reference()).isEmpty(),
                        "Explicit resume restored a transient force or anchor configuration");
                require(region.status() == StructurePhysicsRegion.Status.PAUSED
                                && region.diagnostic().equals("saved_geometry_mismatch"),
                        "Changed saved shape did not pause recovery: " + region.status() + " " + region.diagnostic());
                require(diagnostics().steps() == 0 && region.motion(moving.reference()).isEmpty()
                                && region.motion(resting.reference()).isEmpty(), "Mismatched cohort published a partial native step");
                assertPhysicsCheckpoint(); assertChest();
                if (age < 3) return;
                put(moving, STONE, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                report.append("geometry_mismatch_paused=true\nmismatch_steps_zero=true\nmismatch_checkpoint_retained=true\n");
                report.append("transient_controls_not_restored=true\n");
                next(Phase.RESUME);
            }
            case RESUME -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED, "Explicit recovery failed: " + region.diagnostic());
                var optional = region.motion(moving.reference());
                if (optional.isEmpty()) return;
                var motion = optional.get();
                var saved = oracle.getCompoundOrEmpty("moving").getCompoundOrEmpty("motion");
                var linear = readVector(saved.getCompoundOrEmpty("linear")); var angular = readVector(saved.getCompoundOrEmpty("angular"));
                require(diagnostics().steps() == 1, "First recovery observation missed the first actual native step");
                require(region.control(moving.reference()).isEmpty() && region.control(resting.reference()).isEmpty(),
                        "First recovery step recreated an old controller");
                require(Math.abs(motion.linearVelocity().x - linear.x) < 0.1 && Math.abs(motion.linearVelocity().z - linear.z) < 0.1
                                && motion.linearVelocity().y > 1 && motion.linearVelocity().y < linear.y
                                && motion.angularVelocity().distanceTo(angular) < 0.15,
                        "First recovery frame lost saved velocity: saved=" + linear + "/" + angular + " actual=" + motion.linearVelocity() + "/" + motion.angularVelocity());
                require(!motion.sleeping(), "Moving recovery unexpectedly slept");
                require(region.motion(resting.reference()).isPresent()
                        && !region.motion(resting.reference()).orElseThrow().sleeping(), "Restored resting body did not rebuild awake contacts");
                resumedPose = moving.pose();
                report.append("first_step_velocity_restored=true\ncontact_rebuild_wakes=true\nfirst_linear=").append(motion.linearVelocity())
                        .append("\nfirst_angular=").append(motion.angularVelocity()).append('\n');
                next(Phase.MOVING);
            }
            case MOVING -> {
                if (age < 5) return;
                require(center(moving).distanceTo(resumedPose.localToWorldPoint(CENTER)) > 0.08,
                        "Restored velocity did not continue actual physical movement");
                require(rotationDifference(moving.pose(), resumedPose) > 1.0E-5, "Restored angular velocity did not continue rotation");
                require(runtime.save(moving.reference()), "Pre-unload checkpoint failed");
                unloaded = moving; unloadedRuntime = moving.runtimeId(); parkedPose = moving.pose();
                require(runtime.unload(moving.reference()), "Moving structure could not unload");
                require(region.motion(moving.reference()).isEmpty(), "Unloaded body still exposes live motion");
                parkedSteps = diagnostics().steps();
                next(Phase.PARKED);
            }
            case PARKED -> {
                require(diagnostics().steps() == parkedSteps, "An unavailable cohort continued native steps");
                require(StructurePhysics.savedRegionBounds(host).isPresent(), "Unload discarded saved region intent");
                if (age < 10) return;
                boolean rejected = false;
                try { unloaded.pose(); } catch (IllegalStateException expected) { rejected = true; }
                require(rejected, "Old unloaded handle remained usable");
                moving = runtime.load(unloaded.reference()).orElseThrow();
                require(!moving.runtimeId().equals(unloadedRuntime), "Reload reused old runtime identity");
                next(Phase.RELOADED);
            }
            case RELOADED -> {
                if (moving.lifecycle() != StructureLifecycle.ACTIVE || region.motion(moving.reference()).isEmpty()) return;
                require(region.status() == StructurePhysicsRegion.Status.READY && diagnostics().steps() > parkedSteps,
                        "Reloaded attachment did not resume its existing cohort");
                require(region.motion(moving.reference()).orElseThrow().linearVelocity().length() > 0.25,
                        "Unload/load discarded live motion");
                require(center(moving).distanceTo(parkedPose.localToWorldPoint(CENTER)) < 0.3,
                        "Paused cohort applied offline catch-up steps");
                assertContents();
                report.append("unload_preserves_intent=true\nparked_steps_unchanged=true\nreload_velocity_restored=true\nold_handle_rejected=true\n");
                require(region.detach(moving.reference()), "Explicit detach did not remove restored member");
                require(runtime.savedPhysicsEntries(host).stream().noneMatch(entry -> entry.reference().equals(moving.reference())),
                        "Explicit detach retained a recoverable association");
                require(StructurePhysics.savedRegionBounds(host).isPresent(), "Detach removed the unrelated member's intent");
                region.close();
                require(StructurePhysics.savedRegionBounds(host).isEmpty() && runtime.savedPhysicsEntries(host).isEmpty(),
                        "Explicit close retained recoverable physics associations");
                require(!StructurePhysics.discardSavedRegion(host), "Discard reported a nonexistent saved region");
                require(runtime.save(moving.reference()) && runtime.save(resting.reference()), "Cleared-intent save failed");
                next(Phase.CLEAR);
            }
            case CLEAR -> {
                require(StructurePhysics.savedRegionBounds(host).isEmpty(), "Ordinary save recreated explicitly cleared intent");
                assertContents(); report.append("detach_clears_only_member=true\nclose_clears_remaining=true\n"); finish();
            }
            case FINISHED -> { }
        }
    }

    private void assertCheckpoint() {
        assertPhysicsCheckpoint();
        assertContents();
    }

    private void assertPhysicsCheckpoint() {
        require(StructurePhysics.savedRegionBounds(host).filter(value -> sameBounds(value, BOUNDS)).isPresent(), "Saved region bounds missing or changed");
        for (var pair : List.of(new BodyEvidence(moving, "moving"), new BodyEvidence(resting, "resting"))) {
            var saved = oracle.getCompoundOrEmpty(pair.key());
            require(samePose(pair.handle().pose(), readPose(saved.getCompoundOrEmpty("pose"))), "Startup or unavailable engine advanced the saved pose");
            var entry = entry(pair.handle());
            require(entry.slot() == saved.getIntOr("slot", -1), "Restart changed permanent slot ownership");
            require(entry.physicsAttachment() != null && entry.physicsAttachment().resumeAllowed(), "Recovery association was lost or disabled");
            require(writeMotion(entry.physicsAttachment().motion()).equals(saved.getCompoundOrEmpty("motion")), "Saved motion/digest/sleep observation changed");
        }
    }

    private void assertContents() {
        require(storage(moving).players().isEmpty() && storage(resting).players().isEmpty(), "Storage must stay playerless");
        require(moving.world().read(STONE).state().is(Blocks.STONE) && resting.world().read(CELL).state().is(Blocks.STONE), "Stored block contents changed");
        assertChest();
    }

    private void assertChest() {
        require(storage(moving).getBlockEntity(CELL) instanceof ChestBlockEntity, "Real chest block entity was not restored");
        var chest = (ChestBlockEntity) storage(moving).getBlockEntity(CELL);
        require(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7, "Checkpoint lost or duplicated chest inventory");
        for (int slot = 1; slot < chest.getContainerSize(); slot++) require(chest.getItem(slot).isEmpty(), "Unexpected fixture chest contents");
        if (oracle != null) require(moving.world().read(CELL).blockEntityData().equals(oracle.getCompoundOrEmpty("moving").getCompoundOrEmpty("chest")),
                "Complete chest NBT changed across checkpoint/recovery");
    }

    private NativeStructureManifest.Entry entry(StructureHandle handle) {
        return runtime.savedPhysicsEntries(host).stream().filter(value -> value.reference().equals(handle.reference())).findFirst().orElseThrow();
    }

    private void assertSavedMotion(StructureHandle handle, StructureMotionState actual) {
        var saved = entry(handle); var attachment = saved.physicsAttachment();
        require(attachment != null && attachment.motion() != null && samePose(saved.pose(), actual.pose()),
                "Saved pose and motion are not a coherent publication");
        var motion = attachment.motion();
        require(motion.linearVelocity().distanceToSqr(actual.linearVelocity()) < 1.0E-18
                        && motion.angularVelocity().distanceToSqr(actual.angularVelocity()) < 1.0E-18
                        && motion.localCenterOfMass().distanceToSqr(actual.localCenterOfMass()) < 1.0E-18
                        && motion.sleeping() == actual.sleeping(), "Checkpoint differs from the actual solver observation");
    }

    private CompoundTag writeBody(StructureHandle handle) {
        var tag = new CompoundTag(); var ref = handle.reference(); var entry = entry(handle);
        tag.putString("id", ref.id().toString()); tag.putString("dimension", ref.dimension().toString());
        tag.putString("backend", ref.backend().toString()); tag.putLong("generation", ref.generation());
        tag.putString("runtime", handle.runtimeId().toString()); tag.putInt("slot", entry.slot());
        tag.put("pose", writePose(handle.pose())); tag.put("motion", writeMotion(entry.physicsAttachment().motion()));
        if (handle == moving) tag.put("chest", handle.world().read(CELL).blockEntityData());
        return tag;
    }

    private void saveHit(CompoundTag tag) {
        var pose = moving.pose();
        var from = pose.localToWorldPoint(CENTER.add(0, 0, 3)); var to = pose.localToWorldPoint(CENTER);
        var hit = StructureQueries.raycast(host, from, to).orElseThrow();
        require(hit.block().structure().equals(moving.reference()) && hit.block().localPosition().equals(CELL), "Checkpoint ray did not target its real chest");
        var value = new CompoundTag(); value.putLong("revision", hit.geometryRevision()); value.putString("runtime", hit.runtimeId().toString());
        value.putString("face", hit.localFace().name()); value.putBoolean("inside", hit.inside());
        value.put("local", writeVector(hit.localPoint())); value.put("world", writeVector(hit.worldPoint()));
        value.put("from", writeVector(from)); value.put("to", writeVector(to)); tag.put("old_hit", value);
    }

    private void rejectOldHit() {
        var old = oracle.getCompoundOrEmpty("old_hit"); var pose = readPose(oracle.getCompoundOrEmpty("moving").getCompoundOrEmpty("pose"));
        var hit = new StructureHit(new SpaceBlockRef(moving.reference(), CELL), UUID.fromString(old.getStringOr("runtime", "")),
                old.getLongOr("revision", -1), pose, Direction.valueOf(old.getStringOr("face", "")),
                readVector(old.getCompoundOrEmpty("local")), readVector(old.getCompoundOrEmpty("world")), old.getBooleanOr("inside", false));
        var from = readVector(old.getCompoundOrEmpty("from")); var to = readVector(old.getCompoundOrEmpty("to"));
        var fresh = StructureQueries.raycast(host, from, to).orElseThrow();
        require(fresh.block().equals(hit.block()) && StructureQueries.validate(host, from, to, fresh).succeeded(), "Fresh checkpoint ray failed after restart");
        require(!StructureQueries.validate(host, from, to, hit).succeeded(), "Old JVM's actual observed hit was accepted after restart");
        report.append("fresh_query_accepted=true\nold_jvm_hit_rejected=true\n");
    }

    private void forceHostChunks() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (host.setChunkForced(x, z, true)) tickets.add(new ChunkPos(x, z));
            host.getChunk(x, z);
        }
    }

    private void releaseTickets() {
        while (!tickets.isEmpty()) { var chunk = tickets.removeFirst(); host.setChunkForced(chunk.x(), chunk.z(), false); }
    }

    private void next(Phase next) { phase = next; age = 0; AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_PHASE stage={} phase={} ticks={}", MODE, phase, ticks); }

    private void finish() {
        require(!complete && !failed, "Probe already finished");
        releaseTickets(); report.append("fixture_tickets_released=true\nticks=").append(ticks).append('\n');
        writeText(resultName(MODE), "PASSED\n" + report); complete = true; next(Phase.FINISHED);
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_PASSED stage={} pid={}", MODE, PID);
        server.halt(false);
    }

    private void guarded(Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            failed = true;
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_FAILED stage={} phase={}", MODE, phase, error);
            try { releaseTickets(); } catch (Throwable release) { error.addSuppressed(release); }
            try { writeText(resultName(MODE), "FAILED\nphase=" + phase + "\nfailure=" + error + "\n" + report); }
            catch (Throwable writing) { error.addSuppressed(writing); }
            // Do not close/detach: preserving failure evidence must not clear recoverable associations.
            server.halt(false);
        }
    }

    private void writeText(String name, String text) {
        try { Files.writeString(directory.resolve(name), text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (IOException error) { throw new IllegalStateException("Cannot write immutable probe result", error); }
    }

    private void writeNbt(String name, CompoundTag tag) {
        try (var output = Files.newOutputStream(directory.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { NbtIo.writeCompressed(tag, output); }
        catch (IOException error) { throw new IllegalStateException("Cannot write immutable checkpoint evidence", error); }
    }

    private static Path verifyDirectory(MinecraftServer server) {
        try {
            var path = Path.of(System.getProperty("academy.nativeStructurePhysicsPersistenceDirectory")).toRealPath();
            var root = Path.of(System.getProperty("academy.nativeStructurePhysicsPersistenceRunRoot")).toRealPath();
            require(path.startsWith(root) && path.getFileName().toString().startsWith("native-structure-physics-persistence-"), "Persistence probe requires a dedicated run directory");
            require(server.getServerDirectory().toRealPath().equals(path), "Actual server directory differs from guarded fixture");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(path.resolve(WORLD)), "Refusing linked or unrelated persistence world");
            require(!Files.exists(path.resolve(resultName(MODE)), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(path.resolve(exitName(MODE)), LinkOption.NOFOLLOW_LINKS), "Probe stage has already run");
            if (MODE.equals("prepare")) require(!Files.exists(path.resolve("checkpoint.nbt"), LinkOption.NOFOLLOW_LINKS), "Prepare requires new checkpoint evidence");
            else {
                var previous = MODE.equals("unavailable") ? "prepare" : "unavailable";
                require(Files.readString(path.resolve(resultName(previous))).startsWith("PASSED\n")
                        && Files.readString(path.resolve(exitName(previous))).contains("exit_status=0\n"), "Previous JVM did not finish and save normally");
            }
            return path;
        } catch (IOException error) { throw new IllegalStateException("Cannot verify persistence probe isolation", error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        var scenario = active;
        if (scenario == null || scenario.server != event.getServer()) return;
        scenario.stopping = true;
        try { scenario.verifyStoppingGuards(); }
        catch (Throwable error) {
            scenario.failed = true;
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_STOPPING_GUARDS_FAILED stage={}", MODE, error);
        }
        try {
            scenario.releaseTickets();
            // Production captures/closes its solver at NORMAL priority before this thaw; no new tick follows.
            if (scenario.frozen) { scenario.server.tickRateManager().setFrozen(false); scenario.frozen = false; }
        } catch (Throwable error) {
            scenario.failed = true;
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_STOPPING_CLEANUP_FAILED stage={}", MODE, error);
        }
    }

    private void verifyStoppingGuards() {
        require(complete && !failed && oracle != null, "Stopping guards require a completed checkpoint scenario");
        var saved = runtime.savedPhysicsEntries(host);
        var reference = readRef(oracle.getCompoundOrEmpty("moving"));
        var pose = readPose(oracle.getCompoundOrEmpty("moving").getCompoundOrEmpty("pose"));
        expectRejected(() -> runtime.load(reference), "Stopping backend allowed loading an existing handle");
        expectRejected(() -> runtime.createEmpty(host, pose), "Stopping backend allowed creating a new permanent owner");
        expectRejected(() -> StructurePhysics.openRegion(host, BOUNDS), "Stopping backend allowed opening a region");
        expectRejected(() -> StructurePhysics.resumeRegion(host), "Stopping backend allowed resuming a region");
        expectRejected(() -> StructurePhysics.discardSavedRegion(host), "Stopping backend allowed discarding recovery intent");
        boolean savedAgain = runtime.save(reference);
        boolean unloadedAgain = runtime.unload(reference);
        require(!savedAgain && !unloadedAgain, "Stopping backend allowed save or unload reentry");
        require(runtime.savedPhysicsEntries(host).equals(saved), "Stopping callbacks changed the saved checkpoint list");
        stoppingGuardsVerified = true;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_STOPPING_GUARDS_PASSED stage={}", MODE);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.watching) return;
        scenario.watching = true;
        Thread.ofPlatform().daemon().name("academy-physics-persistence-exit").start(() -> {
            try { scenario.server.getRunningThread().join(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            int status = scenario.complete && !scenario.failed && scenario.stopping && scenario.stoppingGuardsVerified
                    && !scenario.server.isCurrentlySaving() ? 0 : 1;
            try {
                var crashDirectory = scenario.directory.resolve("crash-reports");
                if (Files.isDirectory(crashDirectory)) try (var files = Files.list(crashDirectory)) { if (files.findAny().isPresent()) status = 1; }
                scenario.writeText(exitName(MODE), "exit_status=" + status + "\nserver_stopped_event=true\nserver_thread_terminated=true\n"
                        + "stopping_guards_verified=" + scenario.stoppingGuardsVerified + "\n");
            } catch (Throwable error) { status = 1; }
            System.out.println("NATIVE_STRUCTURE_PHYSICS_PERSISTENCE_EXIT stage=" + MODE + " status=" + status);
            ServerLifecycleHooks.handleExit(status);
        });
    }

    private record BodyEvidence(StructureHandle handle, String key) {}
    private static String resultName(String stage) { return "native-physics-persistence-" + stage + "-result.txt"; }
    private static String exitName(String stage) { return "native-physics-persistence-" + stage + "-exit.txt"; }
    private ServerLevel storage(StructureHandle handle) { return runtime.storageLevel(handle.reference()).orElseThrow(); }
    private NativeStructurePhysics.Diagnostics diagnostics() { return ((NativeStructurePhysics.Region) region).diagnostics(); }
    private static Vec3 center(StructureHandle handle) { return handle.pose().localToWorldPoint(CENTER); }
    private static void put(StructureHandle handle, BlockPos pos, StructureBlockData data) {
        require(handle.world().compareAndSet(pos, handle.world().read(pos), data) == StructureWriteResult.APPLIED, "Fixture block write failed");
    }
    private static void requireDifferentProcess(CompoundTag tag) {
        require(tag.getLongOr("pid", -1) != PID || tag.getLongOr("process_start", -1) != PROCESS_START, "Probe must use another real JVM");
    }
    private static void expectRejected(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, message);
    }
    private static CompoundTag writeMotion(NativeStructurePhysicsAttachment.Motion motion) {
        require(motion != null, "Checkpoint has no coherent saved motion");
        var tag = new CompoundTag(); tag.put("linear", writeVector(motion.linearVelocity())); tag.put("angular", writeVector(motion.angularVelocity()));
        tag.put("com", writeVector(motion.localCenterOfMass())); tag.putBoolean("sleeping", motion.sleeping()); tag.putString("digest", motion.geometryDigest()); return tag;
    }
    private static CompoundTag writeVector(Vec3 value) { var tag = new CompoundTag(); tag.putDouble("x", value.x); tag.putDouble("y", value.y); tag.putDouble("z", value.z); return tag; }
    private static Vec3 readVector(CompoundTag tag) { return new Vec3(tag.getDoubleOr("x", Double.NaN), tag.getDoubleOr("y", Double.NaN), tag.getDoubleOr("z", Double.NaN)); }
    private static CompoundTag writePose(StructurePose pose) { var tag = writeVector(pose.worldOrigin()); tag.putDouble("qx", pose.rotationX()); tag.putDouble("qy", pose.rotationY()); tag.putDouble("qz", pose.rotationZ()); tag.putDouble("qw", pose.rotationW()); return tag; }
    private static StructurePose readPose(CompoundTag tag) { return new StructurePose(readVector(tag), tag.getDoubleOr("qx", Double.NaN), tag.getDoubleOr("qy", Double.NaN), tag.getDoubleOr("qz", Double.NaN), tag.getDoubleOr("qw", Double.NaN)); }
    private static StructureRef readRef(CompoundTag tag) { return new StructureRef(UUID.fromString(tag.getStringOr("id", "")), Identifier.parse(tag.getStringOr("dimension", "")), Identifier.parse(tag.getStringOr("backend", "")), tag.getLongOr("generation", -1)); }
    private static StructurePose poseAt(Vec3 center, Quaterniond rotation) { var basis = new StructurePose(Vec3.ZERO, rotation); return new StructurePose(center.subtract(basis.localToWorldDirection(CENTER)), rotation); }
    private static boolean sameBounds(AABB a, AABB b) { return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ; }
    private static boolean samePose(StructurePose a, StructurePose b) { return a.worldOrigin().distanceToSqr(b.worldOrigin()) < 1.0E-18 && rotationDifference(a, b) < 1.0E-14; }
    private static double rotationDifference(StructurePose a, StructurePose b) { return 1 - Math.abs(a.rotationX() * b.rotationX() + a.rotationY() * b.rotationY() + a.rotationZ() * b.rotationZ() + a.rotationW() * b.rotationW()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
