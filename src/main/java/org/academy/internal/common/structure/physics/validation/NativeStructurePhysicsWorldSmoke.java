package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.portal.TeleportTransition;
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
import org.academy.internal.common.structure.physics.NativeStructurePhysicsGeometry;
import org.joml.Quaterniond;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in server actions against real storage and host worlds; never simulates keyboard actions. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructurePhysicsWorldSmoke {
    public static final String CLIENT_WORLD = "Native Structure Physics Smoke";
    private static final BlockPos CELL = new BlockPos(0, 80, 0);
    private static final BlockPos SLAB = CELL.east();
    private static final BlockPos STAIR = CELL.north();
    private static final BlockPos ADDED = CELL.above();
    private static final Vec3 LOCAL_CENTER = Vec3.atCenterOf(CELL);
    private static final List<BlockPos> CELLS = List.of(CELL, SLAB, STAIR, ADDED);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructurePhysicsSmoke");
    private static volatile NativeStructurePhysicsWorldSmoke active;
    private static volatile ClientState clientState = new ClientState("WAITING", List.of(), null, null, "", false, false, null);
    private static volatile String capturedStage = "";
    private static volatile String capturePath = "";
    private enum Phase { ACTIVATE, BEFORE, FALL, LANDED, TERRAIN_FALL, TERRAIN_LAND,
        LOCAL_ADD, LOCAL_REMOVE, UNSUPPORTED, SUPPORTED_AGAIN, FROZEN, THAWED,
        PAIR_ARM, PAIR, EMPTY, UNLOAD, RELOAD, BOUNDARY, FINISHED }

    public record Frame(StructureRef reference, UUID runtimeId, long revision, StructurePose pose) {}
    public record ClientState(String phase, List<Frame> recentFrames, StructureRef first, StructureRef second,
                              String capture, boolean scenarioFinished, boolean complete, String failure) {}

    private final MinecraftServer server;
    private final ServerLevel host;
    private final NativeStructureRuntime runtime;
    private final Path directory;
    private final String role;
    private final UUID playerId;
    private final List<Frame> frames = new ArrayList<>();
    private final List<ChunkPos> fixtureTickets = new ArrayList<>();
    private final StringBuilder evidence = new StringBuilder("actions=server_fixture\nactual_keyboard_actions=false\n");
    private StructureHandle a, b;
    private StructurePhysicsRegion region;
    private AABB bounds;
    private BlockPos base;
    private Phase phase = Phase.ACTIVATE;
    private int ticks, phaseTicks, stableTicks, exitStatus = 1;
    private boolean sawGravity, compoundMassVerified, addedLowerFloor, fixtureFreeze, complete, orderlyStop, watching;
    private long beforeEpoch, beforeContent, beforeTerrain, pausedSteps;
    private double beforeMass;
    private StructurePose firstPose, frozenPose, savedPose;
    private UUID oldRuntime;
    private BlockPos farChunkProbe;
    private String failure;

    private NativeStructurePhysicsWorldSmoke(MinecraftServer server, UUID playerId, String role) {
        this.server = server;
        this.playerId = playerId;
        this.role = role;
        directory = verifyDirectory(server, role);
        host = server.overworld();
        runtime = NativeStructureRuntime.backend(server);
    }

    public static ClientState clientState() { return clientState; }

    public static void captured(String stage, Path path) {
        capturePath = path.toAbsolutePath().toString();
        capturedStage = stage;
    }

    public static void beginClient(MinecraftServer server, UUID playerId) {
        if (!ENABLED || !"client".equals(System.getProperty("academy.nativeStructurePhysicsSmokeMode"))) {
            throw new IllegalStateException("Structure physics client probe was not explicitly enabled");
        }
        begin(server, playerId, "client");
    }

    public static void finishClient(MinecraftServer server) {
        var scenario = active;
        require(scenario != null && scenario.server == server && server.isSameThread(), "Wrong physics smoke owner");
        require(scenario.phase == Phase.FINISHED && scenario.failure == null, "Server scenario is not complete");
        scenario.guarded(scenario::finish);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (ENABLED && "server".equals(System.getProperty("academy.nativeStructurePhysicsSmokeMode"))) {
            begin(event.getServer(), null, "server");
        }
    }

    private static void begin(MinecraftServer server, UUID playerId, String role) {
        require(server.isSameThread() && active == null, "Physics smoke requires one server-thread owner");
        // No fixture world mutation or forced exit is armed until these path checks succeed.
        var scenario = new NativeStructurePhysicsWorldSmoke(server, playerId, role);
        active = scenario;
        scenario.guarded(scenario::initialize);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.complete || scenario.failure != null) return;
        scenario.guarded(scenario::advance);
    }

    private void initialize() {
        var player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (playerId != null) require(player != null && player.level() == host, "Camera player must remain in the host overworld");
        int x = player == null ? 8 : player.blockPosition().getX();
        int z = player == null ? 8 : player.blockPosition().getZ();
        int y = Math.min(host.getMaxY() - 20, Math.max(110, player == null ? 110 : player.blockPosition().getY() + 24));
        base = new BlockPos(x, y, z);
        bounds = new AABB(x - 10, y - 6, z - 10, x + 10, y + 10, z + 10);
        // Only fixture preparation explicitly loads these known chunks. The boundary case does not.
        for (int cx = (x - 11) >> 4; cx <= (x + 11) >> 4; cx++) {
            for (int cz = (z - 11) >> 4; cz <= (z + 11) >> 4; cz++) {
                if (host.setChunkForced(cx, cz, true)) fixtureTickets.add(new ChunkPos(cx, cz));
                host.getChunk(cx, cz);
            }
        }
        evidence.append("fixture_forced_chunks=").append(fixtureTickets.size()).append('\n');
        for (var pos : BlockPos.betweenClosed(base.offset(-9, -5, -9), base.offset(9, 9, 9))) {
            require(host.getBlockState(pos).isAir() && host.getBlockEntity(pos) == null,
                    "Refusing to replace existing host content at " + pos);
        }
        a = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow(() ->
                new IllegalStateException("Physics smoke requires two unused permanent storage slots"));
        b = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow(() ->
                new IllegalStateException("Physics smoke requires a second unused permanent storage slot"));
        evidence.append("first_ref=").append(a.reference()).append("\nsecond_ref=").append(b.reference()).append('\n');
        if (player != null) positionCamera(player);
        publish("");
    }

    private void advance() {
        require(++ticks < 2800, "Structure physics smoke exceeded its total deadline at " + phase);
        require(++phaseTicks < 650, "Structure physics smoke phase timed out: " + phase + diagnostic());
        if (playerId != null) {
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null && player.level() == host, "Camera player left its host world");
            player.setDeltaMovement(Vec3.ZERO);
        }
        if (phase != Phase.ACTIVATE && phase != Phase.UNLOAD && phase != Phase.RELOAD && phase != Phase.BOUNDARY && phase != Phase.FINISHED
                && phase != Phase.UNSUPPORTED && phase != Phase.FROZEN) {
            require(region == null || region.status() == StructurePhysicsRegion.Status.READY,
                    "Unexpected physics region status: " + diagnostic());
        }
        publish(phase == Phase.BEFORE ? "before" : phase == Phase.LANDED ? "landed" : "");
        switch (phase) {
            case ACTIVATE -> {
                if (a.lifecycle() != StructureLifecycle.ACTIVE || b.lifecycle() != StructureLifecycle.ACTIVE) return;
                require(storage(a).players().isEmpty() && storage(b).players().isEmpty(), "Storage must remain playerless");
                put(a, CELL, Blocks.STONE.defaultBlockState());
                put(a, SLAB, Blocks.STONE_SLAB.defaultBlockState());
                put(a, STAIR, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                require(storage(a).getBlockState(STAIR).getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT,
                        "Compound fixture requires a real straight stair");
                var geometry = NativeStructurePhysicsGeometry.captureStructure(storage(a));
                require(geometry.nonAirCells() == 3 && geometry.boxes().size() >= 4,
                        "Real stone/slab/stair geometry must preserve partial cuboids");
                evidence.append("initial_cells=3\ninitial_boxes=").append(geometry.boxes().size()).append('\n');
                floor(base.getY(), true);
                firstPose = poseAt(base.getX() + 0.5, base.getY() + 6, base.getZ() + 0.5,
                        new Quaterniond().rotationXYZ(0.28, 0.1, 0.2));
                a.setPose(firstPose);
                region = StructurePhysics.openRegion(host, bounds);
                next(Phase.BEFORE);
            }
            case BEFORE -> {
                if (!runtime.isPresented(a.reference())) return;
                if (!captureReady("before")) return;
                recordCapture("before");
                region.attach(a);
                beforeContent = NativeStructurePhysicsGeometry.captureStructure(storage(a)).sourceRevision();
                next(Phase.FALL);
            }
            case FALL -> {
                var motion = region.motion(a.reference()).orElseThrow();
                if (!compoundMassVerified) {
                    require(Math.abs(motion.mass() - 2.25) < 1.0E-8,
                            "Stone + bottom slab + straight stair must have native mass 2.25, got " + motion.mass());
                    evidence.append("compound_mass=").append(motion.mass()).append('\n');
                    compoundMassVerified = true;
                }
                sawGravity |= motion.linearVelocity().y < -0.3 && center(a).y < firstPose.localToWorldPoint(LOCAL_CENTER).y - 0.2;
                require(motion.contentRevision() == beforeContent, "Rigid movement must not rewrite storage geometry");
                require(minY(a) >= base.getY() + 0.85, "Compound crossed its host platform");
                if (!settled(a, base.getY() + 1)) return;
                require(sawGravity && center(a).y < firstPose.localToWorldPoint(LOCAL_CENTER).y - 2,
                        "Real structure did not fall before landing");
                require(rotationDifference(firstPose, a.pose()) > 1.0E-4, "Contact did not evolve the non-yaw orientation");
                evidence.append("gravity=true\nnon_yaw_contact=true\nfirst_landing_min_y=").append(minY(a)).append('\n');
                next(Phase.LANDED);
            }
            case LANDED -> {
                if (!captureReady("landed")) return;
                recordCapture("landed");
                beforeTerrain = diagnostics().terrainEpoch();
                frozenPose = a.pose();
                floor(base.getY(), false);
                next(Phase.TERRAIN_FALL);
            }
            case TERRAIN_FALL -> {
                if (center(a).y > frozenPose.localToWorldPoint(LOCAL_CENTER).y - 0.4) return;
                require(region.motion(a.reference()).orElseThrow().linearVelocity().y < -0.3,
                        "Removing real host support did not resume gravity");
                require(diagnostics().terrainEpoch() > beforeTerrain, "Host removal did not rebuild terrain geometry");
                floor(base.getY() - 3, true);
                addedLowerFloor = true;
                next(Phase.TERRAIN_LAND);
            }
            case TERRAIN_LAND -> {
                require(addedLowerFloor && minY(a) >= base.getY() - 2.2, "Structure crossed newly added terrain");
                if (!settled(a, base.getY() - 2)) return;
                evidence.append("terrain_remove_fall=true\nterrain_add_contact=true\nterrain_epoch=")
                        .append(diagnostics().terrainEpoch()).append('\n');
                var motion = region.motion(a.reference()).orElseThrow();
                beforeEpoch = motion.geometryEpoch(); beforeContent = motion.contentRevision(); beforeMass = motion.mass();
                put(a, ADDED, Blocks.STONE.defaultBlockState());
                next(Phase.LOCAL_ADD);
            }
            case LOCAL_ADD -> {
                var motion = region.motion(a.reference()).orElseThrow();
                if (motion.contentRevision() <= beforeContent || motion.geometryEpoch() <= beforeEpoch) return;
                require(motion.mass() > beforeMass + 0.5, "Added live storage block was not included in rigid mass");
                require(NativeStructurePhysicsGeometry.captureStructure(storage(a)).nonAirCells() == 4,
                        "Added storage cell is missing from extraction");
                beforeEpoch = motion.geometryEpoch(); beforeContent = motion.contentRevision();
                put(a, ADDED, Blocks.AIR.defaultBlockState());
                next(Phase.LOCAL_REMOVE);
            }
            case LOCAL_REMOVE -> {
                var motion = region.motion(a.reference()).orElseThrow();
                if (motion.contentRevision() <= beforeContent || motion.geometryEpoch() <= beforeEpoch) return;
                require(Math.abs(motion.mass() - beforeMass) < 1.0E-6, "Removing storage cell left stale rigid mass");
                evidence.append("live_content_add_remove=true\ngeometry_epoch=").append(motion.geometryEpoch()).append('\n');
                verifyRejectedControls();
                frozenPose = a.pose(); pausedSteps = diagnostics().steps();
                put(a, ADDED, Blocks.COBWEB.defaultBlockState());
                next(Phase.UNSUPPORTED);
            }
            case UNSUPPORTED -> {
                require(region.status() == StructurePhysicsRegion.Status.PAUSED
                                && region.diagnostic().contains("unsupported_content"),
                        "Unsupported real storage geometry must pause: " + diagnostic());
                require(diagnostics().steps() == pausedSteps && samePose(a.pose(), frozenPose),
                        "Unsupported geometry advanced or published motion");
                if (phaseTicks < 10) return;
                evidence.append("unsupported_geometry_paused=true\nunsupported_pause_ticks=").append(phaseTicks).append('\n');
                put(a, ADDED, Blocks.AIR.defaultBlockState());
                next(Phase.SUPPORTED_AGAIN);
            }
            case SUPPORTED_AGAIN -> {
                require(diagnostics().steps() > pausedSteps && region.motion(a.reference()).isPresent(),
                        "Replacing unsupported geometry did not resume the same region");
                require(!server.tickRateManager().isFrozen() && server.tickRateManager().frozenTicksToRun() == 0,
                        "Freeze fixture requires an initially running server");
                evidence.append("supported_geometry_resumed=true\n");
                frozenPose = a.pose(); pausedSteps = diagnostics().steps();
                fixtureFreeze = true;
                server.tickRateManager().setFrozen(true);
                next(Phase.FROZEN);
            }
            case FROZEN -> {
                require(server.tickRateManager().isFrozen() && !server.tickRateManager().runsNormally(),
                        "Real server tick manager did not freeze world simulation");
                require(region.status() == StructurePhysicsRegion.Status.PAUSED
                                && region.diagnostic().equals("host_unavailable_or_tick_freeze"),
                        "Frozen world must pause physics: " + diagnostic());
                require(diagnostics().steps() == pausedSteps && samePose(a.pose(), frozenPose),
                        "Frozen world advanced or published physics motion");
                if (phaseTicks < 10) return;
                evidence.append("vanilla_freeze_paused=true\nfreeze_pause_ticks=").append(phaseTicks).append('\n');
                releaseFixtureFreeze();
                next(Phase.THAWED);
            }
            case THAWED -> {
                require(!server.tickRateManager().isFrozen() && server.tickRateManager().runsNormally()
                                && diagnostics().steps() > pausedSteps,
                        "Unfreezing the world did not resume the same physics region");
                evidence.append("vanilla_freeze_resumed=true\n");
                preparePair();
                next(Phase.PAIR_ARM);
            }
            case PAIR_ARM -> {
                require(region.motion(a.reference()).isPresent() && region.motion(b.reference()).isPresent(),
                        "Both pair bodies must be built before setting their velocities");
                region.setVelocity(a.reference(), new Vec3(5, 0, 0), Vec3.ZERO);
                region.setVelocity(b.reference(), new Vec3(-5, 0, 0), Vec3.ZERO);
                next(Phase.PAIR);
            }
            case PAIR -> {
                var left = center(a); var right = center(b);
                require(left.x < right.x && right.x - left.x > 0.82, "Attached structures crossed or overlapped: " + left + "/" + right);
                if (phaseTicks < 75) return;
                require(right.x - left.x < 1.25, "Dynamic pair never reached each other");
                require(diagnostics().bodies() == 2, "Both structures must remain bodies in one shared region");
                evidence.append("shared_session=").append(diagnostics().sessionId()).append("\ndynamic_pair_contact=true\n");
                put(a, CELL, Blocks.AIR.defaultBlockState());
                frozenPose = a.pose();
                next(Phase.EMPTY);
            }
            case EMPTY -> {
                require(region.motion(a.reference()).isEmpty(), "Empty attached structure retained a rigid body");
                require(samePose(a.pose(), frozenPose), "Empty structure continued moving");
                require(diagnostics().bodies() == 1, "Empty structure did not release its body");
                require(a.lifecycle() == StructureLifecycle.ACTIVE && runtime.resolve(a.reference()).orElseThrow() == a,
                        "Empty geometry must preserve persistent owner/active handle");
                if (phaseTicks < 20) return;
                savedPose = b.pose(); oldRuntime = b.runtimeId(); pausedSteps = diagnostics().steps();
                require(runtime.unload(b.reference()), "Fixture save/unload failed");
                next(Phase.UNLOAD);
            }
            case UNLOAD -> {
                require(region.status() == StructurePhysicsRegion.Status.PAUSED
                                && region.diagnostic().equals("awaiting_structure_activation"),
                        "Unloaded member must park its physics cohort: " + diagnostic());
                require(diagnostics().steps() == pausedSteps, "Parked physics cohort continued stepping");
                require(region.motion(b.reference()).isEmpty() && diagnostics().bodies() == 0, "Unloaded body was not released");
                require(runtime.resolve(b.reference()).isEmpty(), "Unloaded handle remained resolvable");
                if (phaseTicks < 20) return;
                evidence.append("unload_cohort_paused=true\nparked_steps_unchanged=true\nunload_pause_ticks=").append(phaseTicks).append('\n');
                b = runtime.load(b.reference()).orElseThrow(() -> new IllegalStateException("Saved fixture did not reload"));
                next(Phase.RELOAD);
            }
            case RELOAD -> {
                if (b.lifecycle() != StructureLifecycle.ACTIVE) return;
                require(!oldRuntime.equals(b.runtimeId()), "Reload must create a new runtime epoch");
                if (region.motion(b.reference()).isEmpty()) return;
                require(b.pose().worldOrigin().distanceTo(savedPose.worldOrigin()) < 0.05,
                        "Restored resting body moved away from its saved pose");
                require(diagnostics().bodies() == 1, "Reload did not restore the saved physical member");
                evidence.append("empty_body_released=true\nowner_retained=true\nunload_body_released=true\nreload_motion_restored=true\n");
                region.close();
                require(region.status() == StructurePhysicsRegion.Status.CLOSED, "Region failed to close");
                releaseHostTickets();
                int farX = base.getX() + 32768;
                farChunkProbe = new BlockPos(farX, base.getY(), base.getZ());
                require(!host.hasChunkAt(farChunkProbe), "Boundary fixture must start in an unloaded chunk");
                b.setPose(poseAt(farX + 0.5, base.getY() + 2, base.getZ() + 0.5, new Quaterniond()));
                frozenPose = b.pose();
                region = StructurePhysics.openRegion(host, new AABB(farX - 4, base.getY() - 2, base.getZ() - 4,
                        farX + 4, base.getY() + 6, base.getZ() + 4));
                region.attach(b);
                next(Phase.BOUNDARY);
            }
            case BOUNDARY -> {
                require(region.status() == StructurePhysicsRegion.Status.PAUSED, "Unloaded host region must pause: " + diagnostic());
                require(diagnostics().steps() == 0 && samePose(frozenPose, b.pose()), "Unloaded region advanced or published motion");
                require(!host.hasChunkAt(farChunkProbe), "Physics implicitly loaded an unavailable host chunk");
                if (phaseTicks < 20) return;
                evidence.append("unloaded_region_paused=true\nunloaded_region_steps=0\nno_implicit_chunk_load=true\n");
                region.close();
                require(region.status() == StructurePhysicsRegion.Status.CLOSED, "Boundary region did not close");
                require(runtime.unload(a.reference()) && runtime.unload(b.reference()), "Final fixture save/unload failed");
                next(Phase.FINISHED);
                if ("server".equals(role)) finish();
            }
            case FINISHED -> { }
        }
    }

    private void verifyRejectedControls() {
        var pose = a.pose();
        var motion = region.motion(a.reference()).orElseThrow();
        long steps = diagnostics().steps();
        boolean rejected = false;
        try { a.setPose(new StructurePose(pose.worldOrigin().add(0, 1, 0), pose.rotation())); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected && samePose(a.pose(), pose), "Attached handle accepted a direct pose change");
        for (var invalid : List.of(new Vec3(Double.NaN, 0, 0), new Vec3(10001, 0, 0))) {
            rejected = false;
            try { region.setVelocity(a.reference(), invalid, Vec3.ZERO); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "Physics accepted invalid linear velocity " + invalid);
        }
        require(region.status() == StructurePhysicsRegion.Status.READY && diagnostics().steps() == steps
                        && region.motion(a.reference()).orElseThrow().equals(motion) && samePose(a.pose(), pose),
                "Rejected controls changed or poisoned the physics region");
        evidence.append("attached_set_pose_rejected=true\ninvalid_velocity_rejected=true\nrejected_controls_preserve_region=true\n");
    }

    private void preparePair() {
        require(region.detach(a.reference()), "Could not detach compound for the next fixture");
        for (var pos : CELLS) if (!pos.equals(CELL)) put(a, pos, Blocks.AIR.defaultBlockState());
        put(b, CELL, Blocks.STONE.defaultBlockState());
        a.setPose(poseAt(base.getX() - 1.5, base.getY() + 0.5, base.getZ() + 0.5, new Quaterniond()));
        b.setPose(poseAt(base.getX() + 2.5, base.getY() + 0.5, base.getZ() + 0.5, new Quaterniond()));
        region.attach(a); region.attach(b);
    }

    private boolean settled(StructureHandle handle, double floorTop) {
        var motion = region.motion(handle.reference()).orElseThrow();
        if (Math.abs(minY(handle) - floorTop) < 0.12 && motion.linearVelocity().lengthSqr() < 0.0064
                && motion.angularVelocity().lengthSqr() < 0.0064) stableTicks++;
        else stableTicks = 0;
        return stableTicks >= 15;
    }

    private double minY(StructureHandle handle) {
        double minimum = Double.POSITIVE_INFINITY;
        var pose = handle.pose();
        var storage = storage(handle);
        for (var pos : CELLS) {
            for (var box : storage.getBlockState(pos).getCollisionShape(storage, pos).toAabbs()) {
                for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                    minimum = Math.min(minimum, pose.localToWorldPoint(new Vec3(pos.getX() + (x == 0 ? box.minX : box.maxX),
                            pos.getY() + (y == 0 ? box.minY : box.maxY), pos.getZ() + (z == 0 ? box.minZ : box.maxZ))).y);
                }
            }
        }
        return minimum;
    }

    private NativeStructurePhysics.Diagnostics diagnostics() { return ((NativeStructurePhysics.Region) region).diagnostics(); }
    private String diagnostic() { return region == null ? " (not opened)" : " " + region.status() + ": " + region.diagnostic(); }
    private Vec3 center(StructureHandle handle) { return handle.pose().localToWorldPoint(LOCAL_CENTER); }
    private ServerLevel storage(StructureHandle handle) { return runtime.storageLevel(handle.reference()).orElseThrow(); }

    private void floor(int y, boolean present) {
        for (var pos : BlockPos.betweenClosed(new BlockPos(base.getX() - 4, y, base.getZ() - 4),
                new BlockPos(base.getX() + 4, y, base.getZ() + 4))) {
            require(present ? host.getBlockState(pos).isAir() : host.getBlockState(pos).is(Blocks.STONE),
                    "Host fixture content unexpectedly changed at " + pos);
            require(host.setBlock(pos, present ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS),
                    "Cannot change host platform at " + pos);
        }
    }

    private void releaseHostTickets() {
        while (!fixtureTickets.isEmpty()) {
            var chunk = fixtureTickets.getFirst();
            host.setChunkForced(chunk.x(), chunk.z(), false);
            fixtureTickets.removeFirst();
        }
    }

    private void releaseFixtureFreeze() {
        if (fixtureFreeze) {
            server.tickRateManager().setFrozen(false);
            fixtureFreeze = false;
        }
    }

    private static void put(StructureHandle handle, BlockPos pos, BlockState state) {
        var before = handle.world().read(pos);
        if (before.state().equals(state)) return;
        require(handle.world().compareAndSet(pos, before, new StructureBlockData(state)) == StructureWriteResult.APPLIED,
                "Storage fixture CAS failed at " + pos);
    }

    private void positionCamera(ServerPlayer player) {
        player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
        var feet = new Vec3(base.getX() + 6.5, base.getY() + 4.5, base.getZ() + 8.5);
        var target = new Vec3(base.getX() + 0.5, base.getY() + 2.5, base.getZ() + 0.5);
        var direction = target.subtract(feet.add(0, player.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        require(player.teleport(new TeleportTransition(host, feet, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING)) == player,
                "Camera positioning was rejected");
    }

    private void next(Phase next) {
        phase = next; phaseTicks = 0; stableTicks = 0;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_PHASE {} tick={}{}", phase, ticks, diagnostic());
        publish("");
    }

    private void publish(String capture) {
        if (a != null && a.lifecycle() == StructureLifecycle.ACTIVE) {
            frames.add(new Frame(a.reference(), a.runtimeId(), a.geometryRevision(), a.pose()));
            if (frames.size() > 32) frames.removeFirst();
        }
        clientState = new ClientState(phase.name(), List.copyOf(frames), a == null ? null : a.reference(),
                b == null ? null : b.reference(), capture, phase == Phase.FINISHED, complete, failure);
    }

    private boolean captureReady(String stage) { return "server".equals(role) || stage.equals(capturedStage); }
    private void recordCapture(String stage) { if ("client".equals(role)) evidence.append(stage).append("_screenshot=").append(capturePath).append('\n'); }

    private void finish() {
        require(!complete && phase == Phase.FINISHED && failure == null, "Invalid probe completion");
        require(fixtureTickets.isEmpty() && !fixtureFreeze, "Fixture host tickets or freeze were not released");
        evidence.append("ticks=").append(ticks).append("\nclosed=true\nfixture_tickets_released=true\n");
        writeResult("PASSED\n" + evidence);
        exitStatus = 0; complete = true; publish("");
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_WORLD_SMOKE_PASSED role={} server_fixture=true ticks={}", role, ticks);
        if ("server".equals(role)) server.halt(false);
    }

    private void guarded(Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PHYSICS_WORLD_SMOKE_FAILED role={} phase={}", role, phase, error);
            try { if (region != null) region.close(); } catch (Throwable closing) { error.addSuppressed(closing); }
            try { releaseFixtureFreeze(); } catch (Throwable thawing) { error.addSuppressed(thawing); }
            try { releaseHostTickets(); } catch (Throwable releasing) { error.addSuppressed(releasing); }
            try { writeResult("FAILED\nphase=" + phase + "\nfailure=" + error + "\n" + evidence); }
            catch (Throwable writing) { error.addSuppressed(writing); }
            publish("");
            if ("server".equals(role)) server.halt(false);
        }
    }

    private void writeResult(String text) {
        try {
            Files.writeString(directory.resolve("native-structure-physics-" + role + "-result.txt"), text,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException error) { throw new IllegalStateException("Cannot save structure physics result", error); }
    }

    public static Path verifyDirectory(MinecraftServer server, String role) {
        try {
            var configured = Path.of(System.getProperty("academy.nativeStructurePhysicsSmokeDirectory")).toRealPath();
            var root = Path.of(System.getProperty("academy.nativeStructurePhysicsSmokeRunRoot")).toRealPath();
            require(configured.startsWith(root) && configured.getFileName().toString().startsWith("native-structure-physics-smoke-" + role),
                    "Physics world probe requires an isolated run directory");
            require(server.getServerDirectory().toRealPath().equals(configured), "Actual server directory differs from the configured probe");
            var expectedWorld = "client".equals(role) ? configured.resolve("saves").resolve(CLIENT_WORLD)
                    : configured.resolve("native-structure-physics-smoke");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(expectedWorld), "Physics world probe refuses linked or unrelated worlds");
            require(!Files.exists(configured.resolve("native-structure-physics-" + role + "-result.txt"), LinkOption.NOFOLLOW_LINKS),
                    "Physics world probe result already exists");
            return configured;
        } catch (IOException error) { throw new IllegalStateException("Cannot verify structure physics probe isolation", error); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var scenario = active;
        if (scenario != null && scenario.server == event.getServer()) {
            scenario.orderlyStop = true;
            scenario.releaseFixtureFreeze();
            scenario.releaseHostTickets();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || !"server".equals(scenario.role) || scenario.watching) return;
        scenario.watching = true;
        Thread.ofPlatform().daemon().name("academy-structure-physics-smoke-exit").start(() -> {
            try { scenario.server.getRunningThread().join(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            int status = scenario.complete && scenario.failure == null && scenario.orderlyStop
                    && !scenario.server.isCurrentlySaving() ? scenario.exitStatus : 1;
            try {
                var crashDirectory = scenario.directory.resolve("crash-reports");
                if (Files.isDirectory(crashDirectory)) try (var paths = Files.list(crashDirectory)) {
                    if (paths.findAny().isPresent()) status = 1;
                }
                Files.writeString(scenario.directory.resolve("native-structure-physics-server-exit.txt"),
                        "exit_status=" + status + "\nserver_stopped_event=true\nserver_thread_terminated=true\n",
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException error) { status = 1; }
            System.out.println("NATIVE_STRUCTURE_PHYSICS_SERVER_THREAD_TERMINATED exit_status=" + status);
            ServerLifecycleHooks.handleExit(status);
        });
    }

    private static StructurePose poseAt(double x, double y, double z, Quaterniond rotation) {
        var basis = new StructurePose(Vec3.ZERO, rotation);
        return new StructurePose(new Vec3(x, y, z).subtract(basis.localToWorldDirection(LOCAL_CENTER)), rotation);
    }

    private static double rotationDifference(StructurePose a, StructurePose b) {
        return 1 - Math.abs(a.rotationX() * b.rotationX() + a.rotationY() * b.rotationY()
                + a.rotationZ() * b.rotationZ() + a.rotationW() * b.rotationW());
    }

    public static boolean samePose(StructurePose a, StructurePose b) {
        return a.worldOrigin().distanceToSqr(b.worldOrigin()) < 1.0E-12 && rotationDifference(a, b) < 1.0E-10;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
