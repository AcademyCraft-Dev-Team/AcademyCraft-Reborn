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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Real-server kinetic throw probe against the public structure services: staged capture,
 * physics-region lift, impulsive launch, in-flight region following and one-shot contact
 * damage. It drives the same public skill entry points used by packets.
 */
final class NativeStructurePhysicsKineticThrowSmokeChecks implements AutoCloseable {
    private enum Phase { PLACE, CHARGE, RELEASE, CAPTURE, PHYSICS, LIFT, THROW, FLIGHT, VERIFY, DONE }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int FLIGHT_OBSERVE_TICKS = 30;
    private static final double EXPECTED_THROW_SPEED = 50.0;
    private static final BlockPos SOURCE_OFFSET = new BlockPos(20, 4, 0);

    private final ServerLevel host;
    private final BlockPos base;
    private final StringBuilder evidence;
    private final List<BlockPos> sourceCells = new ArrayList<>();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private BlockPos source;
    private BlockPos seed;
    private StructureRef reference;
    private StructurePhysicsRegion region;
    private Zombie zombie;
    private double initialZombieHealth;
    private double damageDrop = -1;
    private double initialComY;
    private double selectionMinY;
    private double firstPhysicsMinY = Double.NaN;
    private double liftMinY = Double.MAX_VALUE;
    private int capturedBlockCount;
    private Vec3 captureOriginPose;
    private AABB previousBounds;
    private int rebinds;
    private int impactTick = -1;
    private int firstImpactTick = -1;
    private int flightTicks;
    private int age, ticks;
    private float initialCp;
    private boolean sawPending, sawAnchor, closed;
    private Phase phase = Phase.PLACE;

    NativeStructurePhysicsKineticThrowSmokeChecks(ServerLevel host, BlockPos base, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                && Boolean.getBoolean("academy.nativeStructurePhysicsKineticThrowSmoke"),
                "Kinetic throw probe is opt-in only");
        this.host = host;
        this.base = base;
        this.evidence = evidence;
        var server = host.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "kinetic-" + UUID.randomUUID().toString().substring(0, 8));
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
        require(++ticks < 900, "Kinetic throw probe timed out at " + phase);
        age++;
        var diagnostic = KineticThrow.Server.diagnostic(player);
        switch (phase) {
            case PLACE -> {
                source = base.offset(SOURCE_OFFSET);
                seed = source.offset(1, 0, 1);
                forceChunks();
                // A real ground platform embeds the 3x3 source in surrounding terrain. The
                // capture removes only the selection lens down to offset -2, so the deeper
                // ground stays as a real floor under the fresh body. The total lens stays
                // inside the 64-box-per-body physics budget.
                for (int x = -1; x <= 3; x++) {
                    for (int z = -1; z <= 3; z++) {
                        for (int y = -5; y <= 0; y++) {
                            var position = source.offset(x, y, z);
                            require(host.getBlockState(position).isAir() && host.getBlockEntity(position) == null,
                                    "Kinetic throw fixture refuses existing host content at " + position);
                            require(host.setBlock(position, Blocks.STONE.defaultBlockState(), FLAGS),
                                    "Cannot place kinetic throw ground at " + position);
                            if (y == 0 && x >= 0 && x < 3 && z >= 0 && z < 3) sourceCells.add(position);
                        }
                    }
                }
                selectionMinY = seed.getY() - KineticThrow.verticalRadius(0);
                evidence.append("kinetic_source_cells=").append(sourceCells.size())
                        .append("\nkinetic_ground_cells=").append(5 * 5 * 6).append('\n');
                aimAt(Vec3.atCenterOf(seed));
                initialCp = AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID());
                next(Phase.CHARGE);
            }
            case CHARGE -> {
                require(KineticThrow.Server.pressHeld(player),
                        "Kinetic throw H press did not start the charge flow");
                evidence.append("kinetic_hpressed_miss_charging=true\n");
                next(Phase.RELEASE);
            }
            case RELEASE -> {
                require(KineticThrow.Server.releaseCharge(player), "Kinetic throw release entry was rejected");
                require(AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID()) < initialCp,
                        "Kinetic throw did not pay its CP cost");
                evidence.append("kinetic_cp_charged=true\n");
                next(Phase.CAPTURE);
            }
            case CAPTURE -> {
                var current = diagnostic.orElse(null);
                if (current == null) {
                    require(age < 300, "Kinetic throw capture never started");
                    return false;
                }
                if (current.stage() == VectorStructureService.Stage.CAPTURING) {
                    sawPending = true;
                    return false;
                }
                reference = current.reference();
                require(reference != null, "Kinetic throw lost its structure reference");
                var handle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Captured structure handle is not active"));
                require(handle.lifecycle() == StructureLifecycle.ACTIVE, "Captured handle is not ACTIVE");
                for (var position : sourceCells) {
                    require(host.getBlockState(position).isAir(), "Captured source cell was not cleared at " + position);
                }
                require(current.blockCount() > sourceCells.size(),
                        "Kinetic throw did not capture the embedded ground: " + current.blockCount());
                capturedBlockCount = current.blockCount();
                captureOriginPose = handle.pose().worldOrigin();
                evidence.append("kinetic_capture_committed=true\nkinetic_capture_pending_observed=")
                        .append(sawPending).append("\nkinetic_source_cleared=true\nkinetic_block_count=")
                        .append(current.blockCount()).append('\n');
                next(Phase.PHYSICS);
            }
            case PHYSICS -> {
                var active = reference == null ? null : StructurePhysics.activeRegion(host, reference).orElse(null);
                if (active == null || active.status() != StructurePhysicsRegion.Status.READY
                        || active.motion(reference).isEmpty()) {
                    require(age < 200, "Kinetic throw physics region never became ready");
                    return false;
                }
                region = active;
                previousBounds = region.bounds();
                var motion = region.motion(reference).orElseThrow();
                initialComY = motion.pose().localToWorldPoint(motion.localCenterOfMass()).y;
                var origin = motion.pose().worldOrigin();
                require(Math.abs(origin.x - captureOriginPose.x) <= 1.0e-3
                                && Math.abs(origin.z - captureOriginPose.z) <= 1.0e-3,
                        "Kinetic throw capture is not horizontally aligned: " + origin
                                + " vs " + captureOriginPose);
                require(motion.mass() > 32.0, "Kinetic throw heavy fixture mass is too light: " + motion.mass());
                firstPhysicsMinY = structureMinY();
                require(firstPhysicsMinY >= selectionMinY - 1.0e-3,
                        "Kinetic throw structure sank below the source selection on its first physics tick: "
                                + firstPhysicsMinY + " < " + selectionMinY);
                evidence.append("kinetic_region_ready=true\nkinetic_motion_present=true\n")
                        .append("kinetic_capture_horizontal_aligned=true\n")
                        .append("kinetic_first_physics_min_y=").append(firstPhysicsMinY)
                        .append("\nkinetic_selection_min_y=").append(selectionMinY)
                        .append("\nkinetic_throw_mass=").append(motion.mass()).append('\n');
                next(Phase.LIFT);
            }
            case LIFT -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "Lift region failed: " + region.diagnostic());
                liftMinY = Math.min(liftMinY, structureMinY());
                require(liftMinY >= selectionMinY - 1.0e-3,
                        "Kinetic throw structure sank below the source selection while lifting: "
                                + liftMinY + " < " + selectionMinY);
                if (region.control(reference).orElse(null) instanceof StructurePhysicsControl.Anchor) {
                    sawAnchor = true;
                }
                var current = diagnostic.orElseThrow(() -> new IllegalStateException(
                        "Kinetic throw context ended during lift"));
                if (!current.lifted()) {
                    require(age < 200, "Kinetic throw never reached its lift height: " + current);
                    return false;
                }
                var motion = region.motion(reference).orElseThrow();
                var raise = motion.pose().localToWorldPoint(motion.localCenterOfMass()).y - initialComY;
                require(sawAnchor, "Kinetic throw lift never installed the shared PD anchor");
                require(raise > 2.5, "Kinetic throw lift only raised the body by " + raise);
                evidence.append("kinetic_lift_anchor=true\nkinetic_lift_rise=").append(raise)
                        .append("\nkinetic_lift_min_y=").append(liftMinY).append('\n');
                next(Phase.THROW);
            }
            case THROW -> {
                spawnZombie();
                var motion = region.motion(reference).orElseThrow();
                var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                snapTo(player, new Vec3(com.x - 6.0, com.y - player.getEyeHeight(), com.z), -90.0f, 0.0f);
                var eye = player.getEyePosition();
                var hit = StructureQueries.raycast(host, eye, eye.add(player.getLookAngle().scale(24.0)))
                        .orElse(null);
                require(hit != null && hit.block().structure().equals(reference),
                        "Kinetic throw launch aim does not hit the held structure");
                require(KineticThrow.Server.pressHeld(player), "Kinetic throw H-press launch entry was rejected");
                var launched = StructureDynamics.motion(host, reference).orElseThrow();
                require(Math.abs(launched.linearVelocity().x - EXPECTED_THROW_SPEED) < 0.5
                                && Math.abs(launched.linearVelocity().y) < 0.5
                                && Math.abs(launched.linearVelocity().z) < 0.5,
                        "Kinetic throw launch velocity is not the converted per-second value: "
                                + launched.linearVelocity());
                require(launched.mass() > 32.0, "Kinetic throw heavy fixture lost its mass before launch");
                require(region.control(reference).isEmpty(), "Kinetic throw retained a control after launch");
                evidence.append("kinetic_hpressed_structure_throw=true\n")
                        .append("kinetic_throw_speed_mass_independent=true\n")
                        .append("kinetic_throw_speed=").append(launched.linearVelocity().x)
                        .append("\nkinetic_throw_mass=").append(launched.mass())
                        .append("\nkinetic_throw_control_cleared=true\n");
                next(Phase.FLIGHT);
            }
            case FLIGHT -> {
                require(region.status() != StructurePhysicsRegion.Status.FAILED,
                        "Follow region failed during flight: " + region.diagnostic());
                if (region.status() == StructurePhysicsRegion.Status.READY
                        && previousBounds != null && !region.bounds().equals(previousBounds)) {
                    rebinds++;
                    previousBounds = region.bounds();
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_KINETIC_REBIND tick={} bounds={}", ticks, previousBounds);
                }
                var pendingDrop = initialZombieHealth - zombie.getHealth();
                if (damageDrop < 0 && pendingDrop > 0.01) {
                    damageDrop = pendingDrop;
                    require(Math.abs(pendingDrop - KineticThrow.structureDamage(capturedBlockCount)) < 0.01,
                            "Kinetic throw contact damage was " + pendingDrop);
                    require(diagnostic.orElseThrow().impacts() >= 1,
                            "Kinetic throw settled a contact without recording it");
                    firstImpactTick = flightTicks;
                    impactTick = flightTicks;
                    evidence.append("kinetic_impact_damage=").append(pendingDrop).append('\n');
                } else if (damageDrop >= 0 && pendingDrop > damageDrop + 0.01) {
                    // The service contract is one settlement per target per impact cooldown;
                    // a heavy body still overlapping the same target must respect it.
                    require(flightTicks - impactTick >= 10,
                            "Kinetic throw settled one target twice within its impact cooldown");
                    evidence.append("kinetic_impact_repeat_ticks=").append(flightTicks - impactTick)
                            .append('\n');
                    damageDrop = pendingDrop;
                    impactTick = flightTicks;
                }
                if (damageDrop >= 0 && firstImpactTick >= 0 && flightTicks == firstImpactTick + 2) {
                    var moved = zombie.position().distanceToSqr(source.getX() + 11.5, source.getY() + 3.0,
                            source.getZ() + 1.5) > 0.04;
                    require(moved || zombie.getDeltaMovement().lengthSqr() > 0.01,
                            "Kinetic throw contact did not knock the target back");
                    evidence.append("kinetic_impact_knockback=true\n");
                }
                flightTicks++;
                if (flightTicks < FLIGHT_OBSERVE_TICKS) return false;
                require(damageDrop >= 0, "Kinetic throw flight never contacted the fixture target");
                require(rebinds >= 1, "Kinetic throw region never followed the inertial structure");
                evidence.append("kinetic_follow_rebinds=").append(rebinds).append("\nkinetic_region_never_failed=true\n");
                // Close the following region in this same tick so a retained inertial body cannot
                // leave it and terminally fail before the verification phase.
                region.close();
                require(region.status() == StructurePhysicsRegion.Status.CLOSED, "Probe region did not close");
                KineticThrow.Server.stopHeld(player);
                require(KineticThrow.Server.diagnostic(player).isEmpty(), "Kinetic throw state survived termination");
                next(Phase.VERIFY);
            }
            case VERIFY -> {
                var handle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow(() ->
                        new IllegalStateException("Retained structure is no longer active"));
                require(handle.lifecycle() == StructureLifecycle.ACTIVE,
                        "Termination staticized or removed the retained structure");
                for (var position : sourceCells) {
                    require(host.getBlockState(position).isAir(), "Termination restored source blocks at " + position);
                }
                var area = new AABB(source).inflate(8.0);
                require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Kinetic throw dropped items instead of retaining the structure");
                evidence.append("kinetic_terminal_structure_active=true\n")
                        .append("kinetic_source_not_restored=true\nkinetic_no_drops=true\n");
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

    private void spawnZombie() {
        require(zombie == null, "Kinetic throw target fixture already exists");
        zombie = EntityTypes.ZOMBIE.create(host, EntitySpawnReason.COMMAND);
        require(zombie != null, "Cannot create the kinetic throw target fixture");
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setPersistenceRequired();
        zombie.setInvulnerable(false);
        var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        require(maxHealth != null, "Zombie attribute fixture is unavailable");
        maxHealth.setBaseValue(1000.0);
        var armor = zombie.getAttribute(Attributes.ARMOR);
        require(armor != null, "Zombie armor attribute fixture is unavailable");
        armor.setBaseValue(0.0);
        // Daylight would otherwise add ambient fire ticks that are not a contact settlement.
        zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 600));
        zombie.setHealth(1000.0f);
        zombie.setPos(source.getX() + 11.5, source.getY() + 3.0, source.getZ() + 1.5);
        require(host.addFreshEntity(zombie), "Cannot register the kinetic throw target fixture");
        initialZombieHealth = zombie.getHealth();
        evidence.append("kinetic_target_health=").append(initialZombieHealth).append('\n');
    }

    /** Lowest world collision box of the captured structure, from its live projection. */
    private double structureMinY() {
        for (var entity : host.getEntitiesOfClass(NativeStructureProjectionEntity.class,
                new AABB(source).inflate(32.0))) {
            if (entity.isRemoved() || !entity.viewState().reference().equals(reference)) continue;
            var boxes = entity.worldCollisionBoxes();
            require(!boxes.isEmpty(), "Kinetic throw structure has no collision boxes");
            double minY = Double.MAX_VALUE;
            for (var box : boxes) minY = Math.min(minY, box.minY);
            return minY;
        }
        throw new IllegalStateException("Kinetic throw structure projection is unavailable");
    }

    private void configure(ServerPlayer target) {
        var system = AbilitySystemServer.getSystem(target);
        var id = target.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(id, 5);
        system.setPlayerBaseMaxCP(id, 100_000);
        system.setPlayerAvailableCP(id, 100_000);
        target.setGameMode(GameType.SURVIVAL);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        for (var skill : new Skill[]{Skills.KINETIC_ENERGY_APPLIED.get(), Skills.KINETIC_THROW.get()}) {
            system.addPlayerSkill(target, skill.getKeyString());
        }
        // Only the skill under test stays enabled: Kinetic Energy Applied doubles ordinary
        // player damage, which would obscure the exact per-contact settlement this probe checks.
        if (Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(target)) {
            system.toggleSkill(id, Skills.KINETIC_ENERGY_APPLIED.get().getKeyString());
        }
        if (!Skills.KINETIC_THROW.get().isEnabled(target)) {
            system.toggleSkill(id, Skills.KINETIC_THROW.get().getKeyString());
        }
        require(Skills.KINETIC_THROW.get().isEnabled(target), "Kinetic throw is not enabled for the probe player");
        require(!Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(target),
                "Kinetic energy applied passive must stay disabled for exact damage settlement");
        for (var sync : new Identifier[]{SyncTypes.ABILITY_CATEGORY, SyncTypes.SKILL_DATA, SyncTypes.CP_DATA}) {
            system.schedulePlayerSync(id, sync);
        }
    }

    private void aimAt(Vec3 point) {
        var eye = point.add(0, 5, 5);
        var direction = point.subtract(eye).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        snapTo(player, new Vec3(eye.x, eye.y - player.getEyeHeight(), eye.z), yaw, pitch);
    }

    private void snapTo(ServerPlayer target, Vec3 feet, float yaw, float pitch) {
        target.snapTo(feet.x, feet.y, feet.z, yaw, pitch);
        target.getAbilities().mayfly = true;
        target.getAbilities().flying = true;
        target.onUpdateAbilities();
        target.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
    }

    private void forceChunks() {
        for (int cx = (source.getX() - 16) >> 4; cx <= (source.getX() + 96) >> 4; cx++) {
            for (int cz = (source.getZ() - 8) >> 4; cz <= (source.getZ() + 8) >> 4; cz++) {
                if (host.setChunkForced(cx, cz, true)) tickets.add(new ChunkPos(cx, cz));
                host.getChunk(cx, cz);
            }
        }
    }

    private void next(Phase value) {
        phase = value;
        age = 0;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_KINETIC_PHASE {} tick={}", phase, ticks);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        KineticThrow.Server.stopHeld(player);
        if (zombie != null && !zombie.isRemoved()) {
            zombie.discard();
            zombie = null;
        }
        if (region != null) {
            try {
                if (region.status() != StructurePhysicsRegion.Status.CLOSED) region.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Kinetic throw probe region cleanup failed", failure);
            }
            region = null;
        }
        if (reference != null) {
            NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(host, reference, sourceCells);
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
                "Kinetic throw fixture did not clean up its embedded player");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
