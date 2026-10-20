package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.WorldBlockTargets;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramStrength;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.program.ServerProgramTargetResolver;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.sublevel.*;

import java.util.ArrayList;
import java.util.UUID;

/** Cross-tick assertions: a node returning COMPLETED alone is not proof of physical control. */
final class SubLevelControlSmoke {
    private static int family;
    private static int phase;
    private static int wait;
    private static int lastServerTick = -1;
    private static UUID captureTransaction;
    private static org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle nozzle;
    private static SubLevelId id;
    private static ProgramStructureReference reference;
    private static ProgramStructureControl control;
    private static ProgramStructureAnchor anchor;
    private static Vec3 target;
    private static double initialDistance;
    private static double closestDistance;
    private static double peakHorizontalSpeed;
    private static Vec3 launchOrigin;
    private static ServerAeromanipProgramRuntime aero;
    private static ServerAcceleratorProgramRuntime vector;
    private static ServerElectromasterProgramRuntime magnet;
    private static volatile boolean complete;

    private SubLevelControlSmoke() {}

    static boolean complete() { return complete; }

    static void tick(MinecraftServer server) {
        if (complete) return;
        if (server.getTickCount() == lastServerTick) return;
        lastServerTick = server.getTickCount();
        var player = server.getPlayerList().getPlayers().getFirst();
        var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
        if (phase == 0) {
            StructureCaptureResult captured;
            if (captureTransaction == null) {
                prepare(player);
                var origin = player.blockPosition().offset(8, 12, family * 4);
                var positions = new ArrayList<BlockPos>();
                int side = 2;
                for (int x = 0; x < side; x++) for (int y = 0; y < side; y++) for (int z = 0; z < side; z++) {
                    var pos = origin.offset(x, y, z);
                    player.level().setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    positions.add(pos);
                }
                captured = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                        positions, 16, 16, (host, pos) -> host.mayInteract(player, pos), true));
            } else {
                captured = StructureTransactions.poll(server, captureTransaction).orElseThrow();
            }
            if (captured.pending()) {
                captureTransaction = captured.transactionId();
                return;
            }
            check(captured.succeeded(), "public matrix capture failed: " + captured);
            id = SubLevelStructureRefs.id(captured.structure().orElseThrow()).orElseThrow();
            captureTransaction = null;
            var body = runtime.get(id).orElseThrow();
            var full = io.netty.buffer.Unpooled.buffer();
            var delta = io.netty.buffer.Unpooled.buffer();
            try {
                var view = runtime.view(id);
                var codec = org.academy.internal.common.network.SubLevelStatePacket.CODEC;
                codec.encode(full, new org.academy.internal.common.network.SubLevelStatePacket(id, body.plot(),
                        body.pose(), runtime.localCollisionBoxes(id), view, false, true));
                codec.encode(delta, new org.academy.internal.common.network.SubLevelStatePacket(id, body.plot(),
                        body.pose(), runtime.localCollisionBoxes(id), view, false, false));
                check(full.readableBytes() - delta.readableBytes() == view.cells().size() * 10 + 1, "delta repeats cells");
                check(codec.decode(full).view().cells().equals(view.cells()), "full snapshot loses cells");
                check(codec.decode(delta).view().isEmpty(), "delta contains cells");
            } finally { full.release(); delta.release(); }
            var identity = SubLevelActivationRegistry.activation(id);
            reference = new ProgramStructureReference(SubLevelStructureRefs.of(body,
                    player.level().dimension().identifier(), identity.generation()), identity.runtimeId());
            if (family == 0) {
                nozzle = new org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle(
                        org.academy.internal.common.world.entity.EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
                nozzle.attachToStructure(player.getUUID(), reference.reference(), BlockPos.ZERO, net.minecraft.core.Direction.DOWN);
                player.level().addFreshEntity(nozzle);
            }
            check(StructureSpaces.resolve(server, reference.reference()).isPresent(), "public resolve");
            check(new ServerProgramTargetResolver(player, 64, 16).structureMotion(
                    new ProgramStructureReference(reference.reference(), UUID.randomUUID())).isEmpty(), "stale runtime accepted");
            control = acquire();
            check(!StructureDirectControl.submit(player.level(), reference.reference(), player, control.controlId(),
                    new StructurePhysicsControl.Force(new Vec3(1_000_000, 0, 0), new Vec3(1, 0, 0))),
                    "out-of-body force point accepted");
            var center = body.pose().localToWorldPoint(body.localBounds().getCenter());
            completed(rotate(new ProgramStructurePose(new ProgramDirection(1, 0, 0), Math.PI / 2)), "rotate X");
            var rotated = body.pose();
            check(runtime.universe().contains(id), "rotation removed physics body");
            check(rotated.localToWorldPoint(body.localBounds().getCenter()).distanceTo(center) < 1.0E-6, "rotation moved center");
            completed(rotate(new ProgramStructurePose(new ProgramDirection(1, 0, 0), Math.PI / 2)), "repeat absolute rotate");
            check(body.pose().rotation().equals(rotated.rotation(), 1.0E-9), "absolute rotation accumulated");
            check(SubLevelActivationRegistry.matches(id, identity.runtimeId(), identity.generation()), "rotation changed identity");
            var from = center.add(0, 0, -4);
            var to = center.add(0, 0, 4);
            var hit = StructureQueries.raycast(player.level(), from, to).orElseThrow();
            check(hit.block().structure().equals(reference.reference()), "query selected wrong body");
            check(WorldBlockTargets.clip(player.level(), null, from, to, ClipContext.Block.COLLIDER).source()
                    == WorldBlockTargets.Source.STRUCTURE, "skill aim missed SubLevel");
            var wall = BlockPos.containing(center.add(0, 0, -2));
            player.level().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            check(StructureQueries.raycast(player.level(), from, to).isEmpty(), "query reached through terrain");
            check(WorldBlockTargets.clip(player.level(), null, from, to, ClipContext.Block.COLLIDER).source()
                    == WorldBlockTargets.Source.TERRAIN, "terrain did not occlude skill aim");
            player.level().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            anchor = new ProgramStructureAnchor(reference, BlockPos.ZERO);
            var point = body.pose().localToWorldPoint(Vec3.atCenterOf(anchor.localPosition()));
            target = point.add(3, 2, 0);
            initialDistance = point.distanceTo(target);
            closestDistance = initialDistance;
            peakHorizontalSpeed = 0;
            // Reproduce a body that slept before receiving its next command.
            body.setState(SubLevelState.SLEEPING);
            completed(move(player, target), "anchor move");
            wait = 0;
            phase = 1;
            return;
        }
        var body = runtime.get(id).orElseThrow();
        if (phase == 1) {
            closestDistance = Math.min(closestDistance,
                    body.pose().localToWorldPoint(Vec3.atCenterOf(anchor.localPosition())).distanceTo(target));
            var velocity = runtime.universe().motion(id).orElseThrow().linearVelocity();
            peakHorizontalSpeed = Math.max(peakHorizontalSpeed, Math.hypot(velocity.x(), velocity.z()));
        }
        if (++wait < 12) return;
        if (phase == 1) {
            double distance = body.pose().localToWorldPoint(Vec3.atCenterOf(anchor.localPosition())).distanceTo(target);
            AcademyCraft.LOGGER.info("SUBLEVEL_CONTROL_MOTION family={} initial_distance={} distance={} motion={}",
                    family, initialDistance, distance, runtime.universe().motion(id).orElseThrow());
            if (family == 0) {
                check(peakHorizontalSpeed > 1 && closestDistance < initialDistance - 0.5,
                        "sleeping jet did not accelerate toward target: peak=" + peakHorizontalSpeed
                                + " closest=" + closestDistance);
            } else {
                check(distance < initialDistance - 0.5, "sleeping body/anchor did not move: " + distance);
            }
            completed(release(control), "release move");
            runtime.universe().setVelocity(id, new PhysicsVector(0, 1, 0), PhysicsVector.ZERO);
            SubLevelMotionController.tick(server);
            SubLevelDirectControl.tick(server);
            check(runtime.universe().motion(id).orElseThrow().linearVelocity().x() == 0, "released move still drives body");
            var old = control;
            control = acquire();
            completed(move(player, target), "successor move");
            completed(release(old), "stale release");
            check(StructureDirectControl.validate(player.level(), reference.reference(), player, control.controlId())
                    == StructureDirectControl.Status.ACTIVE, "stale release revoked successor");
            completed(launch(), "launch");
            var velocity = runtime.universe().motion(id).orElseThrow().linearVelocity();
            check(velocity.x() > 0, "launch did not impart velocity");
            SubLevelMotionController.tick(server);
            SubLevelDirectControl.tick(server);
            check(Math.abs(runtime.universe().motion(id).orElseThrow().linearVelocity().x() - velocity.x()) < 1.0E-6,
                    "move overwrote launch");
            completed(release(control), "release launch");
            launchOrigin = body.pose().worldOrigin();
            wait = 0;
            phase = 2;
            return;
        }
        check(body.pose().worldOrigin().x > launchOrigin.x + 0.1, "released launch did not travel");
        check(runtime.universe().contains(id), "release deleted physical body");
        AcademyCraft.LOGGER.info("SUBLEVEL_CONTROL_MATRIX_PASSED family={} sleep_anchor_move=true absolute_rotation=true public_aim=true wall_occlusion=true release=true launch_inertia=true", family);
        runtime.remove(id);
        if (nozzle != null) { nozzle.discard(); nozzle = null; }
        if (++family == 3) complete = true;
        phase = 0;
    }

    private static void prepare(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var uuid = player.getUUID();
        system.setPlayerAbilityCategory(uuid, switch (family) {
            case 0 -> AbilityCategories.AEROMANIP.get();
            case 1 -> AbilityCategories.ACCELERATOR.get();
            default -> AbilityCategories.ELECTROMASTER.get();
        });
        system.setPlayerLevel(uuid, 5);
        system.setPlayerBaseMaxCP(uuid, 100_000);
        system.setPlayerAvailableCP(uuid, 100_000);
        var skill = switch (family) { case 0 -> Skills.HIGH_SPEED_JET.get(); case 1 -> Skills.KINETIC_THROW.get(); default -> Skills.MAGNET_MANIPULATION.get(); };
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(uuid, skill.getKeyString());
        if (family == 0) {
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(uuid, system.getAeromanipResourceManager().getCapacity(player));
        }
        aero = new ServerAeromanipProgramRuntime(player);
        vector = new ServerAcceleratorProgramRuntime(player);
        magnet = new ServerElectromasterProgramRuntime(player);
    }

    private static ProgramStructureControl acquire() {
        return switch (family) {
            case 0 -> { var result = aero.controlStructure(reference); completed(result.result(), "control"); yield result.control(); }
            case 1 -> { var result = vector.controlStructure(reference); completed(result.result(), "control"); yield result.control(); }
            default -> { var result = magnet.controlStructure(reference); completed(result.result(), "control"); yield result.control(); }
        };
    }

    private static ProgramStructureOperationResult move(ServerPlayer player, Vec3 point) {
        var world = new ProgramWorldPosition(player.level().dimension().identifier(), point.x, point.y, point.z);
        return switch (family) { case 0 -> aero.moveStructure(control, world, anchor, 2).result(); case 1 -> vector.moveStructure(control, world, anchor, 2).result(); default -> magnet.moveStructure(control, world, anchor, 2).result(); };
    }

    private static ProgramStructureOperationResult rotate(ProgramStructurePose pose) {
        return switch (family) { case 0 -> aero.rotateStructure(control, pose).result(); case 1 -> vector.rotateStructure(control, pose).result(); default -> magnet.rotateStructure(control, pose).result(); };
    }

    private static ProgramStructureOperationResult launch() {
        var direction = new ProgramDirection(1, 0, 0);
        return switch (family) { case 0 -> aero.launchStructure(control, direction, 1, 1).result(); case 1 -> vector.launchStructure(control, direction, AcceleratorProgramStrength.STANDARD).result(); default -> magnet.launchStructure(control, direction, 1).result(); };
    }

    private static ProgramStructureOperationResult release(ProgramStructureControl token) {
        return switch (family) { case 0 -> aero.releaseStructure(token).result(); case 1 -> vector.releaseStructure(token).result(); default -> magnet.releaseStructure(token).result(); };
    }

    private static void completed(ProgramStructureOperationResult result, String action) {
        check(result.status() == ProgramStructureOperationResult.Status.COMPLETED, action + ": " + result);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("family=" + family + " " + message);
    }
}
