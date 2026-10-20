package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureHandle;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.util.List;
import java.util.UUID;

/** Real tick-by-tick direct controls; exceptions fail the owning isolated client smoke. */
final class SubLevelDirectSkillSmoke {
    private static volatile int phase;
    private static int ticks;
    private static int lastServerTick = -1;
    private static StructureRef reference;
    private static SubLevelId id;
    private static Vec3 start;
    private static Vec3 direction;
    private static HighSpeedJetNozzle nozzle;
    private static HighSpeedJetStructureService.LaunchHandle jet;
    private static MagneticStructureService.Session magnetic;
    private static LivingEntity target;
    private static float targetHealth;
    private static int magneticTicks;
    private static UUID legacyTransaction;
    private static Vec3 legacyPlayerPosition;
    private static Vec3 legacyLocalPoint;
    private static StructurePose legacyOriginalPose;
    private static int legacyCellCount;
    private static BlockPos jetSource;
    private static StructurePhysicsRegion legacyRegion;
    private static StructureRef sharedReference;
    private static StructurePose sharedOriginalPose;
    private static UUID sharedTransaction;
    private static StructurePhysicsLease sharedGuard;
    private static Vec3 originalPlayerPosition;
    private static Vec3 originalPlayerVelocity;
    private static float originalPlayerYaw;
    private static float originalPlayerPitch;
    private static boolean originalPlayerNoGravity;
    private static double peakJetSpeed;
    private static BlockPos magneticShapeOccluder;

    private SubLevelDirectSkillSmoke() {
    }

    static boolean complete() {
        return phase == 15;
    }

    static void tick(MinecraftServer server) {
        if (complete()) return;
        if (lastServerTick == server.getTickCount()) return;
        lastServerTick = server.getTickCount();
        var player = server.getPlayerList().getPlayers().getFirst();
        var level = player.level();
        var runtime = SubLevelServer.runtime(level).orElseThrow();
        ticks++;
        if (phase == 1 && target != null) {
            var diagnostic = HighSpeedJetStructureService.diagnostic(player).orElseThrow();
            var body = runtime.get(id).orElseThrow();
            peakJetSpeed = Math.max(peakJetSpeed, diagnostic.speed());
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_JET_MOTION tick={} speed={} peak={} center={}"
                            + " host_obstacles={} impacts={} target_health={} motion={}",
                    ticks, diagnostic.speed(), peakJetSpeed, body.worldCenter(),
                    hostObstacles(player, body.worldBounds().inflate(0.1)), diagnostic.impacts(), target.getHealth(),
                    StructureDynamics.motion(level, reference));
        }
        if (phase == 0) {
            originalPlayerPosition = player.position();
            originalPlayerVelocity = player.getDeltaMovement();
            originalPlayerYaw = player.getYRot();
            originalPlayerPitch = player.getXRot();
            originalPlayerNoGravity = player.isNoGravity();
            enable(player, Skills.HIGH_SPEED_JET.get());
            // A saved world's nearby trees, hills and earlier fixtures must not turn the
            // propulsion assertion into a terrain-stop assertion. Verify, never clear, the lane.
            var testPosition = new Vec3(player.getX(), level.getMaxY() - 80, player.getZ());
            for (int x = -3; x <= 3; x++) for (int z = -1; z <= 3; z++) {
                level.getChunk((BlockPos.containing(testPosition).getX() >> 4) + x,
                        (BlockPos.containing(testPosition).getZ() >> 4) + z);
            }
            var corridor = new AABB(testPosition.x - 40, testPosition.y - 4, testPosition.z - 12,
                    testPosition.x + 40, testPosition.y + 16, testPosition.z + 40);
            require(hostObstacles(player, corridor) == 0, "high-altitude jet corridor contains host collision geometry");
            require(runtime.subLevels().stream().noneMatch(body -> body.worldBounds().intersects(corridor)),
                    "high-altitude jet corridor intersects another SubLevel");
            require(level.getEntitiesOfClass(NativeStructureProjectionEntity.class, corridor).isEmpty(),
                    "high-altitude jet corridor intersects a legacy structure");
            player.connection.teleport(testPosition.x, testPosition.y, testPosition.z, 0, 0);
            player.setDeltaMovement(Vec3.ZERO);
            var seed = player.blockPosition().offset(6, 5, 0);
            jetSource = seed;
            level.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            level.setBlock(seed.west(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
            nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), level);
            nozzle.attach(player.getUUID(), seed, Direction.NORTH);
            require(level.addFreshEntity(nozzle), "cannot place jet nozzle");
            jet = HighSpeedJetStructureService.launch(player,
                    new HighSpeedJetStructureService.LaunchRequest(seed, Direction.NORTH, null, 2, 1, 120, true),
                    1).orElseThrow(() -> new IllegalStateException("direct jet rejected"));
            advance();
        } else if (phase == 1 && target == null) {
            var diagnostic = HighSpeedJetStructureService.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("direct jet capture or adoption failed"));
            require(ticks <= 200, "direct jet transaction did not complete");
            if (diagnostic.stage() == HighSpeedJetStructureService.Stage.CAPTURING) return;
            reference = diagnostic.reference();
            id = SubLevelStructureRefs.id(reference).orElseThrow();
            require(nozzle.isAttachedTo(reference), "jet nozzle failed to follow captured structure");
            start = runtime.get(id).orElseThrow().worldCenter();
            target = net.minecraft.world.entity.EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            require(target != null, "cannot create impact target");
            target.setPos(start.x, start.y - 0.5, start.z + 2.5);
            target.setNoGravity(true);
            ((Mob) target).setNoAi(true);
            require(level.addFreshEntity(target), "cannot add impact target");
            targetHealth = target.getHealth();
            peakJetSpeed = diagnostic.speed();
            ticks = 0;
        } else if (phase == 1 && ticks >= 10) {
            var diagnostic = HighSpeedJetStructureService.diagnostic(player).orElseThrow();
            require(peakJetSpeed > 8, "jet peak speed below calibrated thrust: peak=" + peakJetSpeed + " " + diagnostic
                    + " source=" + jetSource + " source_state=" + level.getBlockState(jetSource)
                    + " start=" + start + " motion=" + StructureDynamics.motion(level, reference));
            require(target.getHealth() < targetHealth && diagnostic.impacts() > 0,
                    "jet did not settle its swept entity impact");
            jet.close();
            require(runtime.get(id).isPresent() && nozzle.activeTicks() == 0,
                    "closing jet discarded structure or left the nozzle active");
            runtime.shift(id, start.subtract(runtime.get(id).orElseThrow().worldCenter()));
            runtime.universe().setVelocity(id, PhysicsVector.ZERO, PhysicsVector.ZERO);
            require(runtime.rotate(id, 90), "cannot rotate retained jet");
            direction = runtime.get(id).orElseThrow().pose().localToWorldDirection(new Vec3(0, 0, 1));
            var activate = new HighSpeedJet.ActivatePacket(false);
            activate.setPacketListener(player.connection);
            HighSpeedJet.Server.handleActivate(activate);
            require(nozzle.activeTicks() > 0, "jet activation packet rejected retained structure nozzle");
            target.discard();
            advance();
        } else if (phase == 2 && ticks >= 8) {
            var motion = StructureDynamics.motion(level, reference).orElseThrow();
            require(motion.linearVelocity().dot(direction) > 1,
                    "reactivated rotated nozzle did not drive along its local face");
            HighSpeedJetStructureService.clear(player);
            require(runtime.get(id).isPresent(), "jet lifecycle clear deleted retained content");
            nozzle.discard();
            runtime.remove(id);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_JET_BEHAVIOR impact=true rotated_reactivation=true retained=true");
            enable(player, Skills.KINETIC_THROW.get());
            player.setYRot(180);
            player.setXRot(0);
            var seed = player.blockPosition().offset(0, 4, -6);
            level.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            aim(player, Vec3.atCenterOf(seed));
            var press = KineticThrow.PressPacket.INSTANCE;
            press.setPacketListener(player.connection);
            KineticThrow.Server.handlePress(press);
            var release = KineticThrow.ReleasePacket.INSTANCE;
            release.setPacketListener(player.connection);
            KineticThrow.Server.handleRelease(release);
            require(VectorStructureService.diagnostic(player).isPresent(), "kinetic charge/release packet rejected");
            start = null;
            advance();
        } else if (phase == 3 && start == null) {
            var diagnostic = VectorStructureService.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("kinetic capture or adoption failed"));
            require(ticks <= 200, "kinetic transaction did not complete");
            if (diagnostic.stage() == VectorStructureService.Stage.CAPTURING) return;
            reference = diagnostic.reference();
            id = SubLevelStructureRefs.id(reference).orElseThrow();
            start = runtime.get(id).orElseThrow().worldCenter();
            ticks = 0;
        } else if (phase == 3 && ticks >= 20) {
            var center = runtime.get(id).orElseThrow().worldCenter();
            require(VectorStructureService.diagnostic(player).orElseThrow().controlActive(),
                    "kinetic held control was not reported active");
            require(center.y > start.y + 0.5, "kinetic anchor did not lift captured structure");
            aim(player, center);
            var eye = player.getEyePosition();
            var range = Skills.KINETIC_THROW.get().scaledRange(player, KineticThrow.TARGET_RANGE);
            var end = eye.add(player.getLookAngle().scale(range));
            var lookedAt = StructureQueries.raycast(level, eye, end);
            var enabled = Skills.KINETIC_THROW.get().isEnabled(player);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_KINETIC_H_AIM enabled={} range={} distance={} hit={}",
                    enabled, range, eye.distanceTo(center), lookedAt.map(hit -> hit.block().structure().id()));
            require(enabled, "kinetic skill became disabled before the H packet");
            require(lookedAt.filter(hit -> hit.block().structure().equals(reference)).isPresent(),
                    "kinetic H aim did not resolve its held SubLevel: eye=" + eye + " look=" + player.getLookAngle()
                            + " center=" + center + " range=" + range + " ray=" + lookedAt);
            var press = KineticThrow.PressPacket.INSTANCE;
            press.setPacketListener(player.connection);
            KineticThrow.Server.handlePress(press);
            require(VectorStructureService.diagnostic(player).orElseThrow().flying(),
                    "kinetic H-press packet rejected held SubLevel");
            start = center;
            advance();
        } else if (phase == 4 && ticks >= 5) {
            var motion = StructureDynamics.motion(level, reference).orElseThrow();
            require(motion.linearVelocity().length() > 20
                    && runtime.get(id).orElseThrow().worldCenter().distanceTo(start) > 1,
                    "kinetic launch did not preserve legacy speed");
            VectorStructureService.clear(player);
            require(runtime.get(id).isPresent(), "kinetic release deleted structure");
            runtime.remove(id);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_VECTOR_BEHAVIOR lift=true h_press=true retained=true");
            enable(player, Skills.MAGNET_MANIPULATION.get());
            var seed = player.blockPosition().offset(5, 4, 0);
            level.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            start = Vec3.atCenterOf(seed);
            magnetic = MagneticStructureService.begin(level, player, List.of(seed)).orElseThrow(
                    () -> new IllegalStateException("magnetic default capture rejected"));
            reference = magnetic.reference();
            id = SubLevelStructureRefs.id(reference).orElseThrow();
            magneticTicks = 0;
            advance();
        } else if (phase == 5) {
            require(magnetic.tick(player, start.add(0, 3, 0), new Vec3(0, 1, 0), 0.8, 0.2, 32, 0),
                    "magnetic existing structure control ended");
            require(ticks <= 200, "magnetic transaction did not complete");
            if (magnetic.stage() == MagneticStructureService.Stage.CAPTURING) return;
            if (++magneticTicks >= 15) {
                require(runtime.get(id).orElseThrow().worldCenter().y > start.y + 0.5,
                        "magnetic control did not move the existing structure");
                magnetic.close();
                require(!StructureDirectControl.occupied(reference,
                        new SubLevelStructureHandle(runtime.get(id).orElseThrow(), runtime.storage(),
                                level.dimension().identifier()).runtimeId()), "magnetic release leaked control");
                require(runtime.get(id).isPresent(), "magnetic release discarded content");
                advance();
            }
        } else if (phase == 6) {
            if (ticks == 1) {
                var center = runtime.get(id).orElseThrow().worldCenter();
                aim(player, center);
                var eye = player.getEyePosition();
                var end = eye.add(player.getLookAngle().scale(20));
                magneticShapeOccluder = BlockPos.containing(eye.lerp(center, 0.5));
                require(level.getBlockState(magneticShapeOccluder).isAir(),
                        "magnetic shape fixture would replace terrain");
                var cobweb = Blocks.COBWEB.defaultBlockState();
                require(cobweb.getCollisionShape(level, magneticShapeOccluder).isEmpty()
                                && !cobweb.getShape(level, magneticShapeOccluder).isEmpty(),
                        "magnetic shape fixture must distinguish collision from outline");
                level.setBlock(magneticShapeOccluder, cobweb, 3);
                var hit = StructureQueries.raycast(level, eye, end, ClipContext.Block.COLLIDER).orElseThrow();
                require(hit.block().structure().equals(reference), "magnetic collision ray missed retained body");
                require(!StructureQueries.validate(level, eye, end, hit).succeeded(),
                        "outline validation must reject a hit behind cobweb selection shape");
                require(StructureQueries.validate(level, eye, end, hit, ClipContext.Block.COLLIDER).succeeded(),
                        "collision validation rejected the visible retained body");
                var pull = MagnetManipulation.MoveStartPacket.TARGET_TO_PLAYER;
                pull.setPacketListener(player.connection);
                MagnetManipulation.Server.handleMoveStart(pull);
                return;
            }
            var diagnostic = MagnetManipulation.Server.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("magnetic R-start packet did not adopt retained body"));
            require(reference.equals(diagnostic.reference()) && diagnostic.controlActive(),
                    "magnetic R-start packet lost the retained body or its control");
            var stop = MagnetManipulation.MoveStopPacket.INSTANCE;
            stop.setPacketListener(player.connection);
            MagnetManipulation.Server.handleMoveStop(stop);
            require(MagnetManipulation.Server.diagnostic(player).isEmpty(),
                    "magnetic R-stop packet left its context active");
            require(!StructureDirectControl.occupied(reference,
                    new SubLevelStructureHandle(runtime.get(id).orElseThrow(), runtime.storage(),
                            level.dimension().identifier()).runtimeId()), "magnetic R-stop packet leaked control");
            require(runtime.get(id).isPresent(), "magnetic R-stop packet discarded content");
            level.setBlock(magneticShapeOccluder, Blocks.AIR.defaultBlockState(), 3);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_MAGNETIC_R_PACKET_PASSED start=true stop=true"
                    + " retained=true collider_validation=true outline_occluded=true");
            advance();
        } else if (phase == 7) {
            runtime.remove(id);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_MAGNETIC_BEHAVIOR adoption=true pull=true release=true");
            enable(player, Skills.KINETIC_THROW.get());
            legacyPlayerPosition = player.position();
            var destination = legacyPlayerPosition.add(128, 24, 0);
            player.teleportTo(destination.x, destination.y, destination.z);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.getChunk((player.blockPosition().getX() >> 4) + dx, (player.blockPosition().getZ() >> 4) + dz);
                }
            }
            reference = null;
            advance();
        } else if (phase == 8) {
            require(ticks <= 200, "legacy static fixture did not become ready");
            var legacy = NativeStructureRuntime.backend(server);
            if (reference == null && legacyTransaction != null) {
                var result = StructureTransactions.poll(server, legacyTransaction).orElseThrow();
                if (result.pending()) return;
                require(result.succeeded(), "legacy fixture capture failed: " + result.detail());
                reference = result.structure().orElseThrow();
            }
            if (reference == null) {
                reference = legacy.loadedReferences().stream()
                        .filter(candidate -> candidate.dimension().equals(level.dimension().identifier()))
                        .filter(candidate -> legacy.attachedPhysicsRegion(level, candidate).isEmpty())
                        .filter(candidate -> legacy.presentedCellCount(candidate) > 0)
                        .findFirst().orElse(null);
                if (reference == null) {
                    var seed = player.blockPosition().offset(0, 3, 6);
                    level.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                            List.of(seed), 1, 24, StructureCaptureRequest.ALLOW, false));
                    require(result.succeeded() || result.pending(), "cannot create legacy static fixture: " + result.detail());
                    legacyTransaction = result.transactionId();
                    if (result.pending()) return;
                    reference = result.structure().orElseThrow();
                }
            }
            var handle = StructureSpaces.resolve(server, reference).orElseThrow();
            var origin = handle.pose().worldOrigin();
            var projection = level.getEntitiesOfClass(NativeStructureProjectionEntity.class,
                            new AABB(origin, origin).inflate(32)).stream()
                    .filter(entity -> entity.viewState().reference().equals(reference)
                            && !entity.worldCollisionBoxes().isEmpty()).findFirst().orElse(null);
            if (projection == null) return;
            legacyOriginalPose = handle.pose();
            legacyCellCount = legacy.presentedCellCount(reference);
            legacyLocalPoint = handle.pose().worldToLocalPoint(projection.worldCollisionBoxes().getFirst().getCenter());
            start = player.getEyePosition().add(0, 0, 6);
            handle.setPose(StructurePose.identity(start.subtract(legacyLocalPoint)));
            advance();
        } else if (phase == 9 && ticks >= 3) {
            require(StructureDynamics.motion(level, reference).isEmpty(), "legacy fixture was already physical");
            aim(player, start);
            var press = KineticThrow.PressPacket.INSTANCE;
            press.setPacketListener(player.connection);
            KineticThrow.Server.handlePress(press);
            var diagnostic = VectorStructureService.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("legacy H packet rejected static structure"));
            require(diagnostic.reference().equals(reference), "legacy H recaptured or replaced the content");
            advance();
        } else if (phase == 10) {
            require(ticks <= 100, "legacy H did not finish physicalizing and launching");
            var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
            if (!diagnostic.flying()) return;
            var motion = StructureDynamics.motion(level, reference).orElseThrow();
            if (motion.linearVelocity().length() <= 20
                    || motion.pose().localToWorldPoint(legacyLocalPoint).distanceTo(start) <= 1) return;
            require(NativeStructureRuntime.backend(server).presentedCellCount(reference) == legacyCellCount,
                    "legacy physicalization changed block ownership");
            VectorStructureService.clear(player);
            legacyRegion = StructurePhysics.attachedRegion(level, reference).orElseThrow();
            legacyRegion.setVelocity(reference, Vec3.ZERO, Vec3.ZERO);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_LEGACY_H_PHYSICALIZATION retained=true h_packet=true speed=true");
            advance();
        } else if (phase == 11) {
            require(ticks <= 200, "shared legacy fixture did not become ready");
            var legacy = NativeStructureRuntime.backend(server);
            if (sharedReference == null && sharedTransaction != null) {
                var result = StructureTransactions.poll(server, sharedTransaction).orElseThrow();
                if (result.pending()) return;
                require(result.succeeded(), "shared legacy capture failed: " + result.detail());
                sharedReference = result.structure().orElseThrow();
            }
            if (sharedReference == null) {
                sharedReference = legacy.loadedReferences().stream()
                        .filter(candidate -> !candidate.equals(reference))
                        .filter(candidate -> candidate.dimension().equals(level.dimension().identifier()))
                        .filter(candidate -> legacy.attachedPhysicsRegion(level, candidate).isEmpty())
                        .filter(candidate -> legacy.presentedCellCount(candidate) > 0)
                        .findFirst().orElse(null);
                if (sharedReference == null) {
                    var seed = player.blockPosition().offset(4, 3, 6);
                    require(level.getBlockState(seed).isAir(), "shared legacy source is occupied");
                    level.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                            List.of(seed), 1, 24, StructureCaptureRequest.ALLOW, false));
                    require(result.succeeded() || result.pending(), "cannot create shared legacy fixture: " + result.detail());
                    sharedTransaction = result.transactionId();
                    if (result.pending()) return;
                    sharedReference = result.structure().orElseThrow();
                }
            }
            var handle = StructureSpaces.resolve(server, sharedReference).orElseThrow();
            var occupied = publishedBounds(player, sharedReference);
            if (occupied == null) return;
            var existingBounds = publishedBounds(player, reference);
            require(existingBounds != null, "first legacy member lost collision geometry");
            var bounds = legacyRegion.bounds();
            Vec3 delta = null;
            for (double x : new double[]{bounds.minX + 1, bounds.maxX - occupied.getXsize() - 1}) {
                for (double y : new double[]{bounds.minY + 1, bounds.maxY - occupied.getYsize() - 1}) {
                    for (double z : new double[]{bounds.minZ + 1, bounds.maxZ - occupied.getZsize() - 1}) {
                        var candidateDelta = new Vec3(x - occupied.minX, y - occupied.minY, z - occupied.minZ);
                        var candidate = occupied.move(candidateDelta);
                        if (bounds.minX <= candidate.minX && bounds.minY <= candidate.minY && bounds.minZ <= candidate.minZ
                                && bounds.maxX >= candidate.maxX && bounds.maxY >= candidate.maxY && bounds.maxZ >= candidate.maxZ
                                && !existingBounds.inflate(1).intersects(candidate)) delta = candidateDelta;
                    }
                }
            }
            require(delta != null, "retained legacy content cannot fit shared fixture region without overlap");
            sharedOriginalPose = handle.pose();
            handle.setPose(new StructurePose(handle.pose().worldOrigin().add(delta), handle.pose().rotation()));
            advance();
        } else if (phase == 12 && ticks >= 3) {
            var motion = legacyRegion.motion(reference).orElseThrow();
            sharedGuard = legacyRegion.acquireControl(reference, UUID.randomUUID(),
                    new StructurePhysicsControl.Force(motion.localCenterOfMass(), Vec3.ZERO), 100).orElseThrow();
            advance();
        } else if (phase == 13) {
            require(sharedGuard.state() == StructurePhysicsLease.State.ACTIVE && sharedGuard.control().isEmpty(),
                    "native leased Force was not consumed while retaining its owner");
            boolean rejected = false;
            try { StructurePhysics.adoptExistingRegion(level, sharedReference); }
            catch (IllegalStateException expected) { rejected = expected.getMessage().contains("active control"); }
            require(rejected && sharedGuard.state() == StructurePhysicsLease.State.ACTIVE,
                    "shared adoption stole another member's consumed Force lease");
            sharedGuard.close();
            var adopted = StructurePhysics.adoptExistingRegion(level, sharedReference);
            require(adopted == legacyRegion, "contained legacy structure opened a conflicting region");
            advance();
        } else if (phase == 14) {
            require(ticks <= 40, "shared region did not rebuild its two bodies");
            if (legacyRegion.status() != StructurePhysicsRegion.Status.READY
                    || legacyRegion.motion(reference).isEmpty() || legacyRegion.motion(sharedReference).isEmpty()) return;
            require(NativeStructureRuntime.backend(server).presentedCellCount(reference) == legacyCellCount,
                    "shared adoption changed retained primary content");
            legacyRegion.detach(sharedReference);
            legacyRegion.detach(reference);
            legacyRegion.close();
            StructureSpaces.resolve(server, reference).orElseThrow().setPose(legacyOriginalPose);
            StructureSpaces.resolve(server, sharedReference).orElseThrow().setPose(sharedOriginalPose);
            player.connection.teleport(originalPlayerPosition.x, originalPlayerPosition.y, originalPlayerPosition.z,
                    originalPlayerYaw, originalPlayerPitch);
            player.setNoGravity(originalPlayerNoGravity);
            player.setDeltaMovement(originalPlayerVelocity);
            AcademyCraft.LOGGER.info("CLIENT_LEGACY_SHARED_REGION_ADOPTION_PASSED same_region=true"
                    + " consumed_force_lease_preserved=true both_bodies_ready=true retained=true");
            advance();
        }
    }

    private static int hostObstacles(ServerPlayer player, AABB bounds) {
        int count = 0;
        for (var position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!player.level().getBlockState(position).getCollisionShape(player.level(), position).isEmpty()) count++;
        }
        return count;
    }

    private static AABB publishedBounds(ServerPlayer player, StructureRef ref) {
        var handle = StructureSpaces.resolve(player.level().getServer(), ref).orElseThrow();
        var origin = handle.pose().worldOrigin();
        AABB result = null;
        for (var projection : player.level().getEntitiesOfClass(NativeStructureProjectionEntity.class,
                new AABB(origin, origin).inflate(32))) {
            if (!projection.viewState().reference().equals(ref)) continue;
            for (var box : projection.worldCollisionBoxes()) result = result == null ? box : result.minmax(box);
        }
        return result;
    }


    private static void enable(ServerPlayer player, Skill skill) {
        var system = AbilitySystemServer.getSystem(player);
        var actor = player.getUUID();
        system.setPlayerAbilityCategory(actor, skill == Skills.HIGH_SPEED_JET.get()
                ? AbilityCategories.AEROMANIP.get() : skill == Skills.KINETIC_THROW.get()
                ? AbilityCategories.ACCELERATOR.get() : AbilityCategories.ELECTROMASTER.get());
        system.setPlayerLevel(actor, 5);
        system.setPlayerBaseMaxCP(actor, 100_000);
        system.setPlayerAvailableCP(actor, 100_000);
        system.setPlayerStatus(actor, org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(actor, skill.getKeyString());
        if (skill == Skills.HIGH_SPEED_JET.get()) {
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(actor, system.getAeromanipResourceManager().getCapacity(player));
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void aim(ServerPlayer player, Vec3 target) {
        var delta = target.subtract(player.getEyePosition());
        var yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        var pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        player.setYHeadRot(yaw);
    }

    private static void advance() {
        phase++;
        ticks = 0;
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
