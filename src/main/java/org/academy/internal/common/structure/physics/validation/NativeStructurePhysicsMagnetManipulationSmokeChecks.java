package org.academy.internal.common.structure.physics.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Real-server magnetic pull probe against the public structure services: magnetically
 * filtered target selection, backend-routed staged capture, shared force control with the
 * converted legacy pull profile, P25-style region following, and a termination that clears
 * control while retaining the captured structure.
 */
final class NativeStructurePhysicsMagnetManipulationSmokeChecks implements AutoCloseable {
    private enum Phase { PLACE, FILTER, START, CAPTURE, PHYSICS, PULL, FOLLOW, STOP, VERIFY, DONE }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockPos SOURCE_OFFSET = new BlockPos(33, 4, 0);
    private static final BlockPos NON_MAGNETIC_OFFSET = new BlockPos(33, 4, 4);
    private static final double EXPECTED_SPEED =
            LegacyMotionUnits.velocityPerSecond(MagneticFieldTuning.targetPullSpeed(0));

    private final ServerLevel host;
    private final BlockPos base;
    private final StringBuilder evidence;
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private BlockPos source;
    private BlockPos seed;
    private BlockPos nonMagnetic;
    private StructureRef reference;
    private StructurePhysicsRegion region;
    private Vec3 initialCenter;
    private Vec3 pullEndCenter;
    private AABB previousBounds;
    private double maxSpeed;
    private float initialCp;
    private int rebinds;
    private int age;
    private int ticks;
    private boolean sawPending;
    private boolean closed;
    private Phase phase = Phase.PLACE;

    NativeStructurePhysicsMagnetManipulationSmokeChecks(ServerLevel host, BlockPos base, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                        && Boolean.getBoolean("academy.nativeStructurePhysicsMagnetManipulationSmoke"),
                "Magnetic pull probe is opt-in only");
        this.host = host;
        this.base = base;
        this.evidence = evidence;
        var server = host.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "magnet-" + UUID.randomUUID().toString().substring(0, 8));
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
        require(++ticks < 900, "Magnetic pull probe timed out at " + phase);
        age++;
        var diagnostic = MagnetManipulation.Server.diagnostic(player);
        switch (phase) {
            case PLACE -> {
                source = base.offset(SOURCE_OFFSET);
                seed = source;
                nonMagnetic = base.offset(NON_MAGNETIC_OFFSET);
                forceChunks();
                require(host.getBlockState(seed).isAir() && host.getBlockEntity(seed) == null,
                        "Magnetic pull fixture refuses existing host content at " + seed);
                require(host.getBlockState(nonMagnetic).isAir() && host.getBlockEntity(nonMagnetic) == null,
                        "Magnetic filter fixture refuses existing host content at " + nonMagnetic);
                require(host.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), FLAGS),
                        "Cannot place the magnetic source block at " + seed);
                require(host.setBlock(nonMagnetic, Blocks.STONE.defaultBlockState(), FLAGS),
                        "Cannot place the non-magnetic control block at " + nonMagnetic);
                evidence.append("mag_source_cells=1\n");
                positionPlayer();
                next(Phase.FILTER);
            }
            case FILTER -> {
                if (age == 1) {
                    aimAt(Vec3.atCenterOf(nonMagnetic));
                    require(MagnetManipulation.Server.startTargetPull(player),
                            "Magnetic pull entry was rejected for the filter probe");
                    return false;
                }
                if (diagnostic.isPresent()) {
                    require(age < 20, "Non-magnetic target did not end the pull");
                    return false;
                }
                require(host.getBlockState(nonMagnetic).is(Blocks.STONE),
                        "Non-magnetic target block was captured or removed");
                evidence.append("mag_filter_stone_rejected=true\n");
                next(Phase.START);
            }
            case START -> {
                if (age == 1) {
                    aimAt(Vec3.atCenterOf(seed));
                    initialCp = AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID());
                }
                require(MagnetManipulation.Server.startTargetPull(player),
                        "Magnetic pull entry was rejected");
                var current = diagnostic.orElse(null);
                if (current == null) {
                    require(age < 300, "Magnetic pull context ended before capture");
                    return false;
                }
                if (current.stage() == MagneticStructureService.Stage.CAPTURING) {
                    sawPending = true;
                    return false;
                }
                reference = current.reference();
                require(reference != null, "Magnetic pull lost its structure reference");
                var handle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Captured structure handle is not active"));
                require(handle.lifecycle() == StructureLifecycle.ACTIVE, "Captured handle is not ACTIVE");
                require(NativeStructureRuntime.ID.equals(reference.backend()),
                        "Magnetic pull did not use the native structure backend: " + reference.backend());
                require(host.getBlockState(seed).isAir(), "Captured source cell was not cleared at " + seed);
                require(current.blockCount() == 1, "Magnetic pull block count differs from the source");
                require(AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID()) < initialCp,
                        "Magnetic pull did not charge its continuous CP after movement started");
                var area = new AABB(source).inflate(24.0);
                require(host.getEntitiesOfClass(BlockStructureEntity.class, area).isEmpty(),
                        "Magnetic pull created a legacy structure entity");
                require(host.getEntitiesOfClass(FallingBlockEntity.class, area).isEmpty(),
                        "Magnetic pull created a legacy falling block entity");
                evidence.append("mag_capture_committed=true\nmag_capture_pending_observed=")
                        .append(sawPending).append("\nmag_source_cleared=true\nmag_block_count=1\n")
                        .append("mag_cp_charged=true\n");
                next(Phase.CAPTURE);
            }
            case CAPTURE -> {
                var active = reference == null ? null : StructurePhysics.activeRegion(host, reference).orElse(null);
                if (active == null || active.status() != StructurePhysicsRegion.Status.READY
                        || active.motion(reference).isEmpty()) {
                    require(age < 200, "Magnetic pull physics region never became ready");
                    return false;
                }
                region = active;
                previousBounds = region.bounds();
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("Magnetic pull context ended before control"));
                require(MagnetManipulation.Server.startTargetPull(player), "Magnetic pull heartbeat was rejected");
                if (!current.controlActive()) {
                    require(age < 200, "Magnetic pull never acquired the shared control lease: " + current);
                    return false;
                }
                var motion = region.motion(reference).orElse(null);
                if (motion == null) return false;
                initialCenter = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                require(current.stage() == MagneticStructureService.Stage.PULLING,
                        "Magnetic pull did not reach its shared control stage");
                evidence.append("mag_region_ready=true\nmag_control_active=true\n");
                next(Phase.PHYSICS);
            }
            case PHYSICS -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "Magnetic pull region failed: " + region.diagnostic());
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("Magnetic pull context ended before the speed check"));
                require(MagnetManipulation.Server.startTargetPull(player), "Magnetic pull heartbeat was rejected");
                require(current.controlActive(), "Magnetic pull lost its control lease");
                var motion = region.motion(reference).orElse(null);
                if (motion == null) return false;
                maxSpeed = Math.max(maxSpeed, motion.linearVelocity().length());
                if (maxSpeed < EXPECTED_SPEED * 0.98 && age < 300) return false;
                require(Math.abs(maxSpeed - EXPECTED_SPEED) <= EXPECTED_SPEED * 0.05,
                        "Magnetic pull speed " + maxSpeed + " is not the converted legacy profile "
                                + EXPECTED_SPEED);
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                require(initialCenter.distanceTo(center) > 2.0, "Magnetic pull never moved the structure");
                evidence.append("mag_expected_speed=").append(EXPECTED_SPEED)
                        .append("\nmag_pull_speed=").append(maxSpeed)
                        .append("\nmag_region_never_failed=true\n");
                pullEndCenter = center;
                next(Phase.PULL);
            }
            case PULL -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "Magnetic pull region failed during following: " + region.diagnostic());
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("Magnetic pull context ended while following"));
                require(MagnetManipulation.Server.startTargetPull(player), "Magnetic pull heartbeat was rejected");
                trackRebase();
                require(current.controlActive(), "Magnetic pull lost its control lease while following");
                if (age == 1) aimAt(pullEndCenter);
                if (age < 8) return false;
                var motion = region.motion(reference).orElseThrow();
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                require(center.distanceTo(initialCenter) > 6.0,
                        "Magnetic pull did not follow the moved caster: " + center.distanceTo(initialCenter));
                evidence.append("mag_pull_travel=").append(center.distanceTo(initialCenter)).append('\n');
                next(Phase.FOLLOW);
            }
            case FOLLOW -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "Magnetic pull region failed during following: " + region.diagnostic());
                if (age == 1) {
                    rebinds = 0;
                    previousBounds = region.bounds();
                    var motion = region.motion(reference).orElseThrow();
                    pullEndCenter = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                    snapTo(player, new Vec3(source.getX() + 44.5, source.getY() + 1.0, source.getZ() - 2.5),
                            player.getYRot(), player.getXRot());
                    aimAt(pullEndCenter);
                    return false;
                }
                var current = diagnostic.orElseThrow(() ->
                        new IllegalStateException("Magnetic pull context ended while following"));
                require(MagnetManipulation.Server.startTargetPull(player), "Magnetic pull heartbeat was rejected");
                trackRebase();
                require(current.controlActive(), "Magnetic pull lost its control lease while following");
                require(age < 200, "Magnetic pull region never followed the moved caster");
                var motion = region.motion(reference).orElseThrow();
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                if (rebinds < 1 || center.distanceTo(pullEndCenter) < 6.0) return false;
                evidence.append("mag_follow_rebinds=").append(rebinds).append('\n');
                next(Phase.STOP);
            }
            case STOP -> {
                require(MagnetManipulation.Server.stopTargetPull(player), "Magnetic pull state was already gone");
                require(MagnetManipulation.Server.diagnostic(player).isEmpty(),
                        "Magnetic pull state survived termination");
                require(region.control(reference).isEmpty(), "Magnetic pull retained its control after termination");
                // Close the following region in this same tick so the retained inertial body cannot
                // leave it and terminally fail before the verification phase.
                region.close();
                require(region.status() == StructurePhysicsRegion.Status.CLOSED, "Magnetic probe region did not close");
                evidence.append("mag_control_cleared=true\n");
                next(Phase.VERIFY);
            }
            case VERIFY -> {
                var handle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Retained structure is no longer active"));
                require(handle.lifecycle() == StructureLifecycle.ACTIVE,
                        "Termination staticized or removed the retained structure");
                require(host.getBlockState(seed).isAir(), "Termination restored the source block at " + seed);
                var area = new AABB(source).inflate(16.0);
                require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Magnetic pull dropped items instead of retaining the structure");
                require(host.getEntitiesOfClass(FallingBlockEntity.class, area).isEmpty(),
                        "Magnetic pull fell back to a legacy falling block entity");
                require(host.getEntitiesOfClass(BlockStructureEntity.class, area).isEmpty(),
                        "Magnetic pull fell back to a legacy structure entity");
                evidence.append("mag_terminal_structure_active=true\nmag_source_not_restored=true\n")
                        .append("mag_no_drops=true\nmag_no_legacy_entity=true\n");
                next(Phase.DONE);
                return finish();
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    private void trackRebase() {
        if (region.status() == StructurePhysicsRegion.Status.READY && previousBounds != null
                && !region.bounds().equals(previousBounds)) {
            rebinds++;
            previousBounds = region.bounds();
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MAGNET_REBIND tick={} bounds={}", ticks, previousBounds);
        }
    }

    private boolean finish() {
        close();
        phase = Phase.DONE;
        return true;
    }

    private void configure(ServerPlayer target) {
        var system = AbilitySystemServer.getSystem(target);
        var id = target.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.ELECTROMASTER.get());
        system.setPlayerLevel(id, 5);
        system.setPlayerBaseMaxCP(id, 100_000);
        system.setPlayerAvailableCP(id, 100_000);
        target.setGameMode(GameType.SURVIVAL);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        for (var skill : new org.academy.api.common.ability.Skill[]{
                Skills.ARC_GENERATE.get(), Skills.MAGNET_MANIPULATION.get()}) {
            system.addPlayerSkill(target, skill.getKeyString());
            if (skill == Skills.MAGNET_MANIPULATION.get() && !skill.isEnabled(target)) {
                system.toggleSkill(id, skill.getKeyString());
            }
        }
        require(Skills.MAGNET_MANIPULATION.get().isEnabled(target),
                "Magnetic manipulation is not enabled for the probe player");
        for (var sync : new Identifier[]{SyncTypes.ABILITY_CATEGORY, SyncTypes.SKILL_DATA, SyncTypes.CP_DATA}) {
            system.schedulePlayerSync(id, sync);
        }
    }

    private void positionPlayer() {
        var feet = new Vec3(source.getX() + 22.5, source.getY() + 1.0, source.getZ() - 2.5);
        snapTo(player, feet, 0.0f, 0.0f);
        aimAt(Vec3.atCenterOf(nonMagnetic));
    }

    private void aimAt(Vec3 point) {
        var eye = player.getEyePosition();
        var direction = point.subtract(eye);
        require(direction.lengthSqr() > 1.0e-6, "Magnetic probe aim is degenerate");
        direction = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        snapTo(player, player.position(), yaw, pitch);
    }

    private void snapTo(ServerPlayer target, Vec3 feet, float yaw, float pitch) {
        target.snapTo(feet.x, feet.y, feet.z, yaw, pitch);
        target.getAbilities().mayfly = true;
        target.getAbilities().flying = true;
        target.onUpdateAbilities();
        target.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
    }

    private void forceChunks() {
        for (int cx = (source.getX() - 16) >> 4; cx <= (source.getX() + 112) >> 4; cx++) {
            for (int cz = (source.getZ() - 16) >> 4; cz <= (source.getZ() + 32) >> 4; cz++) {
                if (host.setChunkForced(cx, cz, true)) tickets.add(new ChunkPos(cx, cz));
                host.getChunk(cx, cz);
            }
        }
    }

    private void next(Phase value) {
        phase = value;
        age = 0;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MAGNET_PHASE {} tick={}", phase, ticks);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        MagnetManipulation.Server.stopTargetPull(player);
        if (region != null) {
            try {
                if (region.status() != StructurePhysicsRegion.Status.CLOSED) region.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Magnetic pull probe region cleanup failed", failure);
            }
            region = null;
        }
        if (reference != null && seed != null) {
            NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(host, reference, List.of(seed));
            reference = null;
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
                "Magnetic pull fixture did not clean up its embedded player");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
