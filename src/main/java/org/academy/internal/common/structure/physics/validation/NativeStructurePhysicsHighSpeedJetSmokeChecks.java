package org.academy.internal.common.structure.physics.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Real-server high-speed jet probe against the public structure services: staged capture through
 * the backend router, shared free-force thrust, calibrated per-tick ceiling, followed flight,
 * and a termination that clears control while retaining the captured structure.
 */
final class NativeStructurePhysicsHighSpeedJetSmokeChecks implements AutoCloseable {
    private enum Phase {
        PLACE, NOZZLE, LAUNCH, CAPTURE, PHYSICS, THRUST, STOP, VERIFY,
        PROGRAM_SETUP, PROGRAM_EXTRACT, PROGRAM_CAPTURE, PROGRAM_MOVE, PROGRAM_MOVE_WAIT,
        PROGRAM_LAUNCH, PROGRAM_LAUNCH_WAIT, PROGRAM_STOP, PROGRAM_VERIFY, DONE
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockPos SOURCE_OFFSET = new BlockPos(0, 4, 32);
    private static final BlockPos PROGRAM_SOURCE_OFFSET = new BlockPos(0, 4, 44);
    private static final float POWER = 1.0f;
    private static final double EXPECTED_SPEED = JetStructureControl.maximumSpeed(POWER);

    private final ServerLevel host;
    private final BlockPos base;
    private final StringBuilder evidence;
    private final List<BlockPos> sourceCells = new ArrayList<>();
    private final List<BlockPos> programSourceCells = new ArrayList<>();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private BlockPos source;
    private BlockPos seed;
    private HighSpeedJetNozzle nozzle;
    private HighSpeedJetStructureService.LaunchHandle handle;
    private StructureRef reference;
    private StructurePhysicsRegion region;
    private Vec3 initialCenter;
    private double initialCp;
    private double initialMp;
    private BlockPos programSeed;
    private StructureRef programReference;
    private StructurePhysicsRegion programRegion;
    private double programInitialZ;
    private int age, ticks;
    private boolean sawPending, programSawPending, closed;
    private Phase phase = Phase.PLACE;

    NativeStructurePhysicsHighSpeedJetSmokeChecks(ServerLevel host, BlockPos base, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                        && Boolean.getBoolean("academy.nativeStructurePhysicsHighSpeedJetSmoke"),
                "High-speed jet probe is opt-in only");
        this.host = host;
        this.base = base;
        this.evidence = evidence;
        var server = host.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "hsjet-" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        player = new ServerPlayer(server, host, profile, cookie.clientInformation());
        connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(connection);
        server.getConnection().getConnections().add(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.markClientLoaded();
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            configure(player);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean advance() {
        require(++ticks < 900, "High-speed jet probe timed out at " + phase);
        age++;
        var diagnostic = HighSpeedJetStructureService.diagnostic(player);
        switch (phase) {
            case PLACE -> {
                source = base.offset(SOURCE_OFFSET);
                seed = source;
                forceChunks();
                require(host.getBlockState(seed).isAir() && host.getBlockEntity(seed) == null,
                        "High-speed jet fixture refuses existing host content at " + seed);
                require(host.setBlock(seed, Blocks.STONE.defaultBlockState(), FLAGS),
                        "Cannot place high-speed jet source block at " + seed);
                sourceCells.add(seed);
                evidence.append("hsjet_source_cells=").append(sourceCells.size()).append('\n');
                positionPlayer();
                var system = AbilitySystemServer.getSystem(player);
                initialCp = system.getPlayerAvailableCP(player.getUUID());
                initialMp = system.getPlayerCurrMP(player.getUUID());
                next(Phase.NOZZLE);
            }
            case NOZZLE -> {
                nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), host);
                nozzle.attach(player.getUUID(), seed, Direction.NORTH);
                require(host.addFreshEntity(nozzle), "Cannot register the high-speed jet nozzle fixture");
                require(nozzle.isAttachedTo(seed, Direction.NORTH), "Jet nozzle did not attach to its support block");
                evidence.append("hsjet_nozzle_placed=true\n");
                next(Phase.LAUNCH);
            }
            case LAUNCH -> {
                handle = HighSpeedJetStructureService.launch(
                        player,
                        new HighSpeedJetStructureService.LaunchRequest(
                                seed,
                                Direction.NORTH,
                                null,
                                HighSpeedJetStructureService.DEFAULT_STRUCTURE_RADIUS,
                                POWER,
                                120,
                                false
                        ),
                        1.0f
                ).orElseThrow(() -> new IllegalStateException("High-speed jet launch entry was rejected"));
                next(Phase.CAPTURE);
            }
            case CAPTURE -> {
                var current = diagnostic.orElse(null);
                if (current == null) {
                    require(age < 300, "High-speed jet capture never started");
                    return false;
                }
                if (current.stage() == HighSpeedJetStructureService.Stage.CAPTURING) {
                    sawPending = true;
                    return false;
                }
                reference = current.reference();
                require(reference != null, "High-speed jet lost its structure reference");
                var structureHandle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Captured structure handle is not active"));
                require(structureHandle.lifecycle() == StructureLifecycle.ACTIVE, "Captured handle is not ACTIVE");
                require(NativeStructureRuntime.ID.equals(reference.backend()),
                        "High-speed jet did not use the native structure backend: " + reference.backend());
                require(host.getBlockState(seed).isAir(), "Captured source cell was not cleared at " + seed);
                require(current.blockCount() == sourceCells.size(),
                        "High-speed jet block count " + current.blockCount() + " differs from the source");
                require(nozzle.isAttachedTo(reference), "Jet nozzle did not follow its structure into capture");
                var system = AbilitySystemServer.getSystem(player);
                require(system.getPlayerAvailableCP(player.getUUID()) < initialCp,
                        "High-speed jet did not pay its CP cost after capture commit");
                require(system.getPlayerCurrMP(player.getUUID()) < initialMp,
                        "High-speed jet did not pay its compressed air after capture commit");
                require(host.getEntitiesOfClass(BlockStructureEntity.class, new AABB(source).inflate(24.0)).isEmpty(),
                        "High-speed jet created a legacy structure entity");
                evidence.append("hsjet_capture_committed=true\nhsjet_capture_pending_observed=")
                        .append(sawPending).append("\nhsjet_source_cleared=true\nhsjet_block_count=")
                        .append(current.blockCount()).append("\nhsjet_resource_charged=true\n")
                        .append("hsjet_nozzle_attached=true\n");
                next(Phase.PHYSICS);
            }
            case PHYSICS -> {
                var active = reference == null ? null : StructurePhysics.activeRegion(host, reference).orElse(null);
                if (active == null) {
                    require(age < 200, "High-speed jet physics region never became ready");
                    return false;
                }
                region = active;
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "High-speed jet region failed before thrust: " + region.diagnostic());
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("High-speed jet context ended before thrust"));
                if (current.stage() != HighSpeedJetStructureService.Stage.THRUSTING || !current.controlActive()) {
                    require(age < 200, "High-speed jet never acquired the shared thrust lease: " + current);
                    return false;
                }
                var motion = region.motion(reference).orElse(null);
                if (motion == null) return false;
                initialCenter = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                evidence.append("hsjet_region_ready=true\nhsjet_control_active=true\n");
                next(Phase.THRUST);
            }
            case THRUST -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "High-speed jet region failed during thrust: " + region.diagnostic());
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("High-speed jet context ended during thrust"));
                if (current.speed() < EXPECTED_SPEED * 0.95 && age < 240) return false;
                require(current.controlActive(), "High-speed jet lost its thrust lease during the ceiling test");
                require(Math.abs(current.speed() - EXPECTED_SPEED) <= EXPECTED_SPEED * 0.05,
                        "High-speed jet speed " + current.speed() + " is not the calibrated ceiling " + EXPECTED_SPEED);
                var motion = region.motion(reference).orElse(null);
                require(motion != null, "High-speed jet lost its motion during thrust");
                require(motion.linearVelocity().z > EXPECTED_SPEED * 0.9,
                        "High-speed jet thrust did not follow the nozzle direction: " + motion.linearVelocity());
                var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                var travel = com.z - initialCenter.z;
                require(travel > 8.0, "High-speed jet did not travel downrange: " + travel);
                evidence.append("hsjet_expected_speed=").append(EXPECTED_SPEED)
                        .append("\nhsjet_launch_speed=").append(current.speed())
                        .append("\nhsjet_thrust_direction=true\nhsjet_travel=").append(travel)
                        .append("\nhsjet_region_never_failed=true\n");
                next(Phase.STOP);
            }
            case STOP -> {
                handle.close();
                require(HighSpeedJetStructureService.diagnostic(player).isEmpty(),
                        "High-speed jet state survived termination");
                require(region.control(reference).isEmpty(), "High-speed jet retained its control after termination");
                // Close the following region in this same tick so the retained inertial body cannot
                // leave it and terminally fail before the verification phase.
                region.close();
                require(region.status() == StructurePhysicsRegion.Status.CLOSED, "Jet probe region did not close");
                evidence.append("hsjet_control_cleared=true\n");
                next(Phase.VERIFY);
            }
            case VERIFY -> {
                var structureHandle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Retained structure is no longer active"));
                require(structureHandle.lifecycle() == StructureLifecycle.ACTIVE,
                        "Termination staticized or removed the retained structure");
                require(host.getBlockState(seed).isAir(), "Termination restored source blocks at " + seed);
                var area = new AABB(source).inflate(8.0);
                require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "High-speed jet dropped items instead of retaining the structure");
                require(host.getEntitiesOfClass(BlockStructureEntity.class, area).isEmpty(),
                        "High-speed jet fell back to a legacy structure entity");
                evidence.append("hsjet_terminal_structure_active=true\n")
                        .append("hsjet_source_not_restored=true\nhsjet_no_drops=true\nhsjet_no_legacy_entity=true\n");
                next(Phase.PROGRAM_SETUP);
            }
            case PROGRAM_SETUP -> {
                // The first capture already asserted its retention; return its permanent slot and
                // stand up a second nozzle fixture for the precision program entry points.
                var first = reference;
                reference = null;
                NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(host, first, sourceCells);
                if (nozzle != null && !nozzle.isRemoved()) nozzle.discard();
                nozzle = null;
                programSeed = base.offset(PROGRAM_SOURCE_OFFSET);
                forceChunks();
                require(host.getBlockState(programSeed).isAir()
                                && host.getBlockEntity(programSeed) == null,
                        "Program fixture refuses existing host content at " + programSeed);
                require(host.setBlock(programSeed, Blocks.STONE.defaultBlockState(), FLAGS),
                        "Cannot place the program source block at " + programSeed);
                programSourceCells.add(programSeed);
                nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), host);
                nozzle.attach(player.getUUID(), programSeed, Direction.NORTH);
                require(host.addFreshEntity(nozzle),
                        "Cannot register the program jet nozzle fixture");
                next(Phase.PROGRAM_EXTRACT);
            }
            case PROGRAM_EXTRACT -> {
                var result = HighSpeedJetStructureService.programExtract(
                        player,
                        programSeed,
                        HighSpeedJetStructureService.DEFAULT_STRUCTURE_RADIUS,
                        0
                );
                require(result.status() == HighSpeedJetStructureService.ProgramStatus.PENDING
                                || result.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED,
                        "Program extraction was rejected: " + result.status() + ":" + result.detail());
                programSawPending = result.status()
                        == HighSpeedJetStructureService.ProgramStatus.PENDING;
                if (result.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED) {
                    programReference = result.reference();
                    require(programReference != null,
                            "Committed program extraction published no reference");
                }
                next(Phase.PROGRAM_CAPTURE);
            }
            case PROGRAM_CAPTURE -> {
                var session = HighSpeedJetStructureService.programSession(player).orElse(null);
                if (session == null) {
                    require(age < 300, "Program capture never committed a session");
                    return false;
                }
                programReference = session.reference();
                var structureHandle = StructureSpaces.resolve(host.getServer(), programReference)
                        .orElseThrow(() -> new IllegalStateException(
                                "Program capture handle is not resolvable"));
                require(structureHandle.lifecycle() == StructureLifecycle.ACTIVE,
                        "Program capture handle is not ACTIVE");
                require(NativeStructureRuntime.ID.equals(programReference.backend()),
                        "Program capture did not use the native structure backend");
                require(host.getBlockState(programSeed).isAir(),
                        "Program capture left its source block at " + programSeed);
                require(HighSpeedJetStructureService.resolvesSupportCell(
                                player, programReference, BlockPos.ZERO),
                        "Program nozzle support cell does not resolve to a captured structure cell");
                var control = HighSpeedJetStructureService.programControl(
                        player, programReference, structureHandle.runtimeId());
                require(control.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED,
                        "Program control was rejected: " + control.detail());
                programRegion = StructurePhysics.activeRegion(host, programReference).orElseThrow(() ->
                        new IllegalStateException("Program session has no active physics region"));
                evidence.append("hsjet_program_extract_pending=").append(programSawPending)
                        .append("\nhsjet_program_session_committed=true")
                        .append("\nhsjet_program_support_resolved=true")
                        .append("\nhsjet_program_control=true\n");
                next(Phase.PROGRAM_MOVE);
            }
            case PROGRAM_MOVE -> {
                var session = HighSpeedJetStructureService.programSession(player).orElseThrow(() ->
                        new IllegalStateException("Program session vanished before its move"));
                var move = HighSpeedJetStructureService.programMove(
                        player, programReference, session.controllerId(), null, null, 60L);
                require(move.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED,
                        "Program move was rejected: " + move.detail());
                var motion = programRegion.motion(programReference).orElseThrow(() ->
                        new IllegalStateException("Program move has no motion"));
                programInitialZ = motion.pose().localToWorldPoint(motion.localCenterOfMass()).z;
                next(Phase.PROGRAM_MOVE_WAIT);
            }
            case PROGRAM_MOVE_WAIT -> {
                require(programRegion.status() != StructurePhysicsRegion.Status.FAILED,
                        "Program region failed during its move: " + programRegion.diagnostic());
                var current = HighSpeedJetStructureService.diagnostic(player).orElse(null);
                if (current == null || current.stage()
                        != HighSpeedJetStructureService.Stage.THRUSTING) {
                    require(age < 240, "Program move never engaged thrust: " + current);
                    return false;
                }
                require(current.controlActive(), "Program move lost its control lease");
                var motion = programRegion.motion(programReference).orElse(null);
                if (motion == null) return false;
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                if (center.z - programInitialZ < 0.25) {
                    require(age < 240, "Program move did not thrust along the local nozzle direction");
                    return false;
                }
                evidence.append("hsjet_program_move_engaged=true\n")
                        .append("hsjet_program_move_shared_direction=true\n");
                next(Phase.PROGRAM_LAUNCH);
            }
            case PROGRAM_LAUNCH -> {
                var session = HighSpeedJetStructureService.programSession(player).orElseThrow(() ->
                        new IllegalStateException("Program session vanished before its launch"));
                var launch = HighSpeedJetStructureService.programLaunch(player, programReference,
                        session.controllerId(), new Vec3(0.0, 0.0, 1.0), POWER, 100L);
                require(launch.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED,
                        "Program launch was rejected: " + launch.detail());
                next(Phase.PROGRAM_LAUNCH_WAIT);
            }
            case PROGRAM_LAUNCH_WAIT -> {
                require(programRegion.status() != StructurePhysicsRegion.Status.FAILED,
                        "Program region failed during its launch: " + programRegion.diagnostic());
                var current = HighSpeedJetStructureService.diagnostic(player).orElseThrow(() ->
                        new IllegalStateException("Program launch context ended early"));
                if (current.speed() < EXPECTED_SPEED * 0.95 && age < 240) return false;
                require(current.controlActive(), "Program launch lost its control lease");
                require(Math.abs(current.speed() - EXPECTED_SPEED) <= EXPECTED_SPEED * 0.05,
                        "Program launch speed " + current.speed()
                                + " is not the shared calibrated ceiling " + EXPECTED_SPEED);
                evidence.append("hsjet_program_launch_speed=").append(current.speed()).append('\n');
                next(Phase.PROGRAM_STOP);
            }
            case PROGRAM_STOP -> {
                var session = HighSpeedJetStructureService.programSession(player).orElseThrow(() ->
                        new IllegalStateException("Program session vanished before release"));
                var released = HighSpeedJetStructureService.programRelease(
                        player, programReference, session.controllerId());
                require(released.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED
                                && released.detail().equals("released"),
                        "Program release was not a real release: " + released.detail());
                var noop = HighSpeedJetStructureService.programRelease(
                        player, programReference, session.controllerId());
                require(noop.status() == HighSpeedJetStructureService.ProgramStatus.COMPLETED
                                && noop.detail().equals("release_noop"),
                        "Program release was not idempotent: " + noop.detail());
                require(HighSpeedJetStructureService.programSession(player).isEmpty(),
                        "Program session survived its release");
                require(programRegion.control(programReference).isEmpty(),
                        "Program release retained its physics control");
                programRegion.close();
                require(programRegion.status() == StructurePhysicsRegion.Status.CLOSED,
                        "Program region did not close after release");
                evidence.append("hsjet_program_release_idempotent=true\n")
                        .append("hsjet_program_control_cleared=true\n");
                next(Phase.PROGRAM_VERIFY);
            }
            case PROGRAM_VERIFY -> {
                var structureHandle = StructureSpaces.resolve(host.getServer(), programReference)
                        .orElseThrow(() -> new IllegalStateException(
                                "Retained program structure is no longer active"));
                require(structureHandle.lifecycle() == StructureLifecycle.ACTIVE,
                        "Program release staticized or removed the retained structure");
                require(host.getBlockState(programSeed).isAir(),
                        "Program release restored source blocks at " + programSeed);
                var area = new AABB(programSeed).inflate(8.0);
                require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Program release dropped items instead of retaining the structure");
                require(host.getEntitiesOfClass(BlockStructureEntity.class, area).isEmpty(),
                        "Program release fell back to a legacy structure entity");
                evidence.append("hsjet_program_retained=true\n")
                        .append("hsjet_program_source_not_restored=true\n");
                next(Phase.DONE);
                return finish();
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    private boolean finish() {
        close();
        phase = Phase.DONE;
        return true;
    }

    private void configure(ServerPlayer target) {
        var system = AbilitySystemServer.getSystem(target);
        var id = target.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.AEROMANIP.get());
        system.setPlayerLevel(id, 5);
        system.setPlayerBaseMaxCP(id, 100_000);
        system.setPlayerAvailableCP(id, 100_000);
        target.setGameMode(GameType.SURVIVAL);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        for (var skill : new org.academy.api.common.ability.Skill[]{
                Skills.AIRFLOW_JET.get(), Skills.HIGH_SPEED_JET.get()}) {
            system.addPlayerSkill(target, skill.getKeyString());
            if (!skill.isEnabled(target)) system.toggleSkill(id, skill.getKeyString());
        }
        require(Skills.HIGH_SPEED_JET.get().isEnabled(target),
                "High-speed jet is not enabled for the probe player");
        system.getAeromanipResourceManager().onPlayerLogin(target);
        system.setPlayerCurrMP(id, system.getAeromanipResourceManager().getCapacity(target));
        for (var sync : new Identifier[]{SyncTypes.ABILITY_CATEGORY, SyncTypes.SKILL_DATA, SyncTypes.CP_DATA}) {
            system.schedulePlayerSync(id, sync);
        }
    }

    private void positionPlayer() {
        var feet = new Vec3(source.getX() + 6.5, source.getY() + 1.0, source.getZ() - 3.5);
        var target = Vec3.atCenterOf(seed);
        var direction = target.subtract(feet.add(0, player.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        player.snapTo(feet.x, feet.y, feet.z, yaw, pitch);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
    }

    private void forceChunks() {
        for (int cx = (source.getX() - 16) >> 4; cx <= (source.getX() + 16) >> 4; cx++) {
            for (int cz = (source.getZ() - 16) >> 4; cz <= (source.getZ() + 112) >> 4; cz++) {
                if (host.setChunkForced(cx, cz, true)) tickets.add(new ChunkPos(cx, cz));
                host.getChunk(cx, cz);
            }
        }
    }

    private void next(Phase value) {
        phase = value;
        age = 0;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_HSJET_PHASE {} tick={}", phase, ticks);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (handle != null) {
            try {
                handle.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("High-speed jet probe handle cleanup failed", failure);
            }
            handle = null;
        }
        HighSpeedJetStructureService.clear(player);
        if (nozzle != null && !nozzle.isRemoved()) {
            nozzle.discard();
            nozzle = null;
        }
        if (region != null) {
            try {
                if (region.status() != StructurePhysicsRegion.Status.CLOSED) region.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("High-speed jet probe region cleanup failed", failure);
            }
            region = null;
        }
        if (programRegion != null) {
            try {
                if (programRegion.status() != StructurePhysicsRegion.Status.CLOSED) {
                    programRegion.close();
                }
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("High-speed jet program region cleanup failed", failure);
            }
            programRegion = null;
        }
        if (reference != null) {
            NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(host, reference, sourceCells);
            reference = null;
        }
        if (programReference != null) {
            NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(
                    host, programReference, programSourceCells);
            programReference = null;
        }
        while (!tickets.isEmpty()) {
            var chunk = tickets.removeFirst();
            host.setChunkForced(chunk.x(), chunk.z(), false);
        }
        var server = host.getServer();
        server.getConnection().getConnections().remove(connection);
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        channel.finishAndReleaseAll();
        require(server.getPlayerList().getPlayer(player.getUUID()) != player && host.getEntity(player.getUUID()) != player
                        && !server.getConnection().getConnections().contains(connection) && !channel.isOpen(),
                "High-speed jet fixture did not clean up its embedded player");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
