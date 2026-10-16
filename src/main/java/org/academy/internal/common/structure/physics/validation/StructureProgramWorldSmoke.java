package org.academy.internal.common.structure.physics.validation;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.structure.*;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.electromaster.magnet.MagneticStructureService;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.ability.program.registry.*;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.util.List;
import java.util.Set;

/** Real compiler/VM/runtime capture-to-release chain; only constructed by isolated smoke fixtures. */
public final class StructureProgramWorldSmoke implements AutoCloseable {
    private final ServerPlayer player;
    private final ServerLevel host;
    private final String category;
    private final BlockPos seed;
    private final ProgramVm.Session vm;
    private final ProgramExecutionFrame frame;
    private final ProgramExecutorLookup executors;
    private final HighSpeedJetNozzle nozzle;
    private StructureRef reference;
    private Vec3 firstCenter;
    private double travel;
    private double approach;
    private int ticks;
    private boolean suspended;
    private int reactivationTicks;
    private double reactivationVelocity;

    public StructureProgramWorldSmoke(ServerPlayer player, String category, BlockPos seed) {
        if (!Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                && !Boolean.getBoolean("academy.sable.smoke")) throw new IllegalStateException("Smoke only");
        this.player = player;
        this.host = (ServerLevel) player.level();
        this.category = category;
        this.seed = seed.immutable();
        host.getChunkAt(seed);
        require(host.getBlockState(seed).isAir(), "Refusing existing fixture content");
        require(host.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS), "Cannot place source");
        player.connection.teleport(seed.getX() + 4.5, seed.getY() + 1, seed.getZ() - 4.5, 0, 0);
        if (category.equals("aeromanip")) {
            nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), host);
            nozzle.attach(player.getUUID(), seed, Direction.DOWN);
            require(host.addFreshEntity(nozzle), "Cannot add nozzle");
        } else nozzle = null;
        var categoryId = Identifier.fromNamespaceAndPath("academy", category);
        var definition = AbilityProgramDefinitions.require(categoryId);
        var extract = new JsonObject();
        extract.addProperty("budget", 1);
        extract.addProperty("tier", 0);
        extract.addProperty("radius", 2);
        extract.addProperty("force_magnetize", false);
        var move = new JsonObject();
        move.addProperty("duration_seconds", 2.0);
        var wait = new JsonObject();
        wait.addProperty("value", 12);
        var graph = new ProgramGraph(List.of(
                node(definition, 0, BaseAbilityProgramDefinition.entryId(categoryId), new JsonObject()),
                node(definition, 1, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, position(seed, false)),
                node(definition, 2, action("extract"), extract),
                node(definition, 3, action("control"), new JsonObject()),
                node(definition, 4, action("move"), move),
                node(definition, 5, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, position(seed.above(2), true)),
                node(definition, 6, CommonProgramNodeIds.WAIT, new JsonObject()),
                node(definition, 7, CommonProgramNodeIds.INTEGER_CONSTANT, wait),
                node(definition, 8, action("release"), new JsonObject())),
                List.of(edge(0, "flow", 2, "flow"), edge(1, "position", 2, "host"),
                        edge(2, "flow", 3, "flow"), edge(2, "structure", 3, "structure"),
                        edge(3, "flow", 4, "flow"), edge(3, "control", 4, "control"),
                        edge(5, "position", 4, "target"), edge(4, "flow", 6, "flow"),
                        edge(7, "value", 6, "ticks"), edge(6, "flow", 8, "flow"),
                        edge(3, "control", 8, "control")));
        var skill = switch (category) {
            case "accelerator" -> "kinetic_throw";
            case "electromaster" -> "magnet_manipulation";
            case "aeromanip" -> "high_speed_jet";
            default -> throw new IllegalArgumentException(category);
        };
        var compiled = definition.compile(graph, Set.of(Identifier.fromNamespaceAndPath("academy", skill)));
        require(compiled.valid(), "Graph compilation: " + compiled.diagnostics());
        vm = new ProgramVm.Session(compiled.program());
        executors = definition.executors();
        Object runtime = switch (category) {
            case "accelerator" -> new ServerAcceleratorProgramRuntime(player);
            case "electromaster" -> new ServerElectromasterProgramRuntime(player);
            default -> new ServerAeromanipProgramRuntime(player);
        };
        var ability = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
        var available = ability.getPlayerAvailableCP(player.getUUID());
        ability.setPlayerAvailableCP(player.getUUID(), 0);
        var deniedFrame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), runtime);
        var denied = new ProgramVm.Session(compiled.program()).run(host.getGameTime(), 1024, executors, deniedFrame);
        require(denied.status() == ProgramVmResult.Status.FAILED && denied.nodeId() == 2,
                "Unpaid extraction reached downstream actions: " + denied);
        require(host.getBlockState(seed).is(Blocks.IRON_BLOCK), "Unpaid extraction mutated source blocks");
        var captured = switch (category) {
            case "accelerator" -> VectorStructureService.diagnostic(player).isPresent();
            case "electromaster" -> MagneticStructureService.programSession(player).isPresent();
            default -> HighSpeedJetStructureService.diagnostic(player).isPresent();
        };
        require(!captured, "Unpaid extraction started a capture session");
        deniedFrame.transaction().abort();
        ability.setPlayerAvailableCP(player.getUUID(), available);
        ability.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        frame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), runtime);
    }

    public boolean advance(StringBuilder evidence) {
        require(++ticks < 420, "Graph timed out at " + vm.currentNodeId());
        if (reactivationTicks > 0) {
            if (++reactivationTicks < (reference.backend().equals(
                    org.academy.internal.common.structure.NativeStructureRuntime.ID) ? 16 : 8)) return false;
            var motion = StructureDynamics.motion(host, reference).orElseThrow();
            require(motion.linearVelocity().y > reactivationVelocity + 1,
                    "Reactivated nozzle only emitted visuals: " + motion.linearVelocity());
            require(HighSpeedJetStructureService.diagnostic(player).isEmpty(), "Reactivation recaptured a structure");
            nozzle.restoreActiveTicks(0);
            evidence.append("aeromanip_retained_structure_nozzle_reactivation=true\n");
            verifyRemovedNativeSupport(evidence);
            return true;
        }
        var observed = switch (category) {
            case "accelerator" -> VectorStructureService.diagnostic(player).map(s -> s.reference());
            case "electromaster" -> MagneticStructureService.programSession(player).map(s -> s.reference());
            default -> HighSpeedJetStructureService.diagnostic(player).map(s -> s.reference());
        };
        if (observed.isPresent()) {
            require(reference == null || reference.equals(observed.get()), "Capture was repeated while pending");
            reference = observed.get();
        }
        if (reference != null) {
            StructureDynamics.motion(host, reference).ifPresent(motion -> {
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                if (firstCenter == null) firstCenter = center;
                travel = Math.max(travel, center.distanceTo(firstCenter));
                var target = Vec3.atCenterOf(seed.above(2));
                approach = Math.max(approach, firstCenter.distanceTo(target) - center.distanceTo(target));
            });
        }
        var result = vm.run(host.getGameTime(), 1024, executors, frame);
        require(result.status() != ProgramVmResult.Status.FAILED, "Graph failed: " + result);
        if (result.status() != ProgramVmResult.Status.COMPLETED) {
            if (result.nodeId() == 2) suspended = true;
            return false;
        }
        require(reference != null && travel > 0.05, "Graph never physically moved: " + travel);
        require(approach > 0.05, "Graph never approached its target: " + approach);
        require(host.getBlockState(seed).isAir(), "Release restored the source");
        require(StructureSpaces.resolve(host.getServer(), reference)
                .filter(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE).isPresent(), "Release lost content");
        require(frame.transaction().commit().successful(), "Graph transaction failed");
        frame.transaction().release();
        StructureAimWorldSmoke.check(player, StructureSpaces.resolve(host.getServer(), reference).orElseThrow(), evidence);
        evidence.append(category).append("_real_graph_capture_move_release=true\n")
                .append(category).append("_insufficient_cp_keeps_source_and_session_empty=true\n")
                .append(category).append("_real_graph_capture_suspended=").append(suspended).append('\n')
                .append(category).append("_real_graph_target_approach=").append(approach).append('\n')
                .append(category).append("_real_graph_travel=").append(travel).append('\n');
        if (nozzle != null) {
            reactivationVelocity = StructureDynamics.motion(host, reference).orElseThrow().linearVelocity().y;
            require(org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet.Server
                    .activateOwnedNozzles(player, 30, 1).contains(nozzle), "Cannot reactivate retained nozzle");
            reactivationTicks = 1;
            return false;
        }
        return true;
    }

    private void verifyRemovedNativeSupport(StringBuilder evidence) {
        if (reference.backend().equals(org.academy.internal.common.structure.NativeStructureRuntime.ID)) {
            var handle = StructureSpaces.resolve(host.getServer(), reference).orElseThrow();
            var cell = handle.world().read(BlockPos.ZERO);
            verifyMissingSupport(() -> require(handle.world().compareAndSet(BlockPos.ZERO, cell,
                    new StructureBlockData(Blocks.AIR.defaultBlockState(), null)) == StructureWriteResult.APPLIED,
                    "Cannot remove fixture nozzle support"), evidence);
        }
    }

    /** Isolated fixtures invalidate support through their backend's real removal path. */
    public void verifyMissingSupport(Runnable removeSupport, StringBuilder evidence) {
        if (nozzle == null) return;
        removeSupport.run();
        nozzle.activate(20);
        nozzle.tick();
        require(nozzle.isAttachedTo(reference) && nozzle.activeTicks() == 0,
                "Missing support kept thrusting or rebound to host terrain");
        evidence.append("aeromanip_missing_support_stops_nozzle_without_rebinding=true\n");
    }

    private Identifier action(String name) {
        return Identifier.fromNamespaceAndPath("academy", "program/" + category + "/action/structure/" + name);
    }

    public StructureRef reference() {
        return reference;
    }

    private JsonObject position(BlockPos pos, boolean center) {
        var value = new JsonObject();
        value.addProperty("dimension", host.dimension().identifier().toString());
        if (center) {
            value.addProperty("x", pos.getX() + 0.5);
            value.addProperty("y", pos.getY() + 0.5);
            value.addProperty("z", pos.getZ() + 0.5);
        } else {
            value.addProperty("x", pos.getX());
            value.addProperty("y", pos.getY());
            value.addProperty("z", pos.getZ());
        }
        return value;
    }

    private static ProgramGraph.Node node(AbilityProgramDefinition definition, int id,
                                          Identifier type, JsonObject config) {
        return new ProgramGraph.Node(id, type, definition.nodeLookup().find(type).schemaVersion(), config);
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }

    @Override public void close() {
        switch (category) {
            case "accelerator" -> org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow.Server.stopHeld(player);
            case "electromaster" -> MagneticStructureService.releasePrograms(player);
            default -> HighSpeedJetStructureService.clear(player);
        }
        if (nozzle != null) nozzle.discard();
        if (reference != null && reference.backend().equals(org.academy.internal.common.structure.NativeStructureRuntime.ID)) {
            StructurePhysics.activeRegion(host, reference).ifPresent(StructurePhysicsRegion::close);
            NativeStructurePhysicsSmokeCleanup.releaseCapturedSlot(host, reference, List.of(seed));
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
